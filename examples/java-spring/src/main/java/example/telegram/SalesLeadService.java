package example.telegram;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Explicitly consent-gated lead capture; Telegram profile identity is never sent to the CRM. */
@Service
public class SalesLeadService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_FIELD_LENGTH = 120;
    private final HubSpotContactsGateway hubspot;
    private final TelegramBotClient telegram;
    private final String managerChatId;
    private final String queueName;
    private final Set<String> disclosedSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> consentedSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> notifiedContactIds = ConcurrentHashMap.newKeySet();

    public SalesLeadService(
            HubSpotContactsGateway hubspot,
            TelegramBotClient telegram,
            @Value("${sales.telegram-manager-chat-id:}") String managerChatId,
            @Value("${sales.queue-name:Demo sales queue}") String queueName) {
        this.hubspot = hubspot;
        this.telegram = telegram;
        this.managerChatId = managerChatId == null ? "" : managerChatId.strip();
        this.queueName = queueName == null || queueName.isBlank() ? "Demo sales queue" : queueName.strip();
    }

    public synchronized Optional<String> handle(String rawText, String chatType, String chatId, String userId) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }
        String[] commandAndArgs = rawText.strip().split("\\s+", 2);
        String command = commandAndArgs[0].split("@", 2)[0];
        if (!Set.of("/sales", "/sales-consent", "/sales-stop", "/sales-submit").contains(command)) {
            return Optional.empty();
        }
        if (!"private".equals(chatType)) {
            return Optional.of("Sales lead capture is available only in a private chat.");
        }
        if (chatId == null || chatId.isBlank() || userId == null || userId.isBlank()) {
            return Optional.of("This request could not be associated with a private chat.");
        }
        String session = chatId + ":" + userId;
        if ("/sales".equals(command)) {
            disclosedSessions.add(session);
            return Optional.of("Sales request: share your email, first name, last name, and company (company may be '-'). After /sales-consent, submit them as /sales-submit email|first name|last name|company. These exact fields will be sent to HubSpot and a short reference will be sent to the sales team. This does not subscribe you to marketing messages. No Telegram username, profile, group membership, or chat history is copied. Use /sales-stop to revoke consent.");
        }
        if ("/sales-consent".equals(command)) {
            if (!disclosedSessions.contains(session)) {
                return Optional.of("Send /sales first to read what fields are shared and with whom.");
            }
            consentedSessions.add(session);
            return Optional.of("Consent recorded in temporary demo memory. Use /sales-submit only when you want to send the listed fields to HubSpot and notify sales.");
        }
        if ("/sales-stop".equals(command)) {
            consentedSessions.remove(session);
            disclosedSessions.remove(session);
            return Optional.of("Sales lead consent revoked. No later submission will be accepted until you read /sales and consent again.");
        }
        if (!consentedSessions.contains(session)) {
            return Optional.of("Read /sales and send /sales-consent before submitting a sales request.");
        }
        if (managerChatId == null || managerChatId.isBlank()) {
            return Optional.of("Sales routing is not configured. No contact data was sent.");
        }
        String arguments = commandAndArgs.length == 2 ? commandAndArgs[1] : "";
        Contact contact = parseContact(arguments);
        if (contact == null) {
            return Optional.of("Use /sales-submit email|first name|last name|company. Use '-' when you do not want to share a company.");
        }

        String contactId;
        try {
            contactId = hubspot.upsert(contact);
        } catch (RuntimeException exception) {
            return Optional.of("The CRM could not accept this request. No sales notification was sent; please try again later or ask for a human.");
        }
        if (notifiedContactIds.add(contactId)) {
            String managerNotice = "New user-submitted sales request routed to " + queueName
                    + ". HubSpot contact reference: " + contactId + ". No Telegram profile data was copied.";
            try {
                telegram.sendOrLog(managerChatId, managerNotice);
            } catch (RuntimeException exception) {
                notifiedContactIds.remove(contactId);
                throw exception;
            }
        }
        return Optional.of("Thanks. Your sales request was submitted. Reference: " + contactId + ".");
    }

    private Contact parseContact(String arguments) {
        String[] fields = arguments.split("\\|", -1);
        if (fields.length != 4) {
            return null;
        }
        String email = fields[0].strip().toLowerCase(Locale.ROOT);
        String firstName = fields[1].strip();
        String lastName = fields[2].strip();
        String company = "-".equals(fields[3].strip()) ? "" : fields[3].strip();
        if (email.length() > 254 || !EMAIL.matcher(email).matches()
                || firstName.isBlank() || firstName.length() > MAX_FIELD_LENGTH
                || lastName.isBlank() || lastName.length() > MAX_FIELD_LENGTH
                || company.length() > MAX_FIELD_LENGTH) {
            return null;
        }
        return new Contact(email, firstName, lastName, company);
    }

    public record Contact(String email, String firstName, String lastName, String company) {}
}
