package example.telegram;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Private employee request demo with server-side roles and two-step manager approval. */
@Service
public class InternalRequestService {
    private static final int MAX_REQUESTS = 1_000;
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);
    private static final Set<String> CATEGORIES = Set.of("IT", "ACCESS", "EQUIPMENT", "OTHER");

    private final InternalRoleDirectory roles;
    private final TelegramBotClient telegram;
    private final String approvalChatId;
    private final Clock clock;
    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Map<Long, String> requestsBySubmissionUpdate = new LinkedHashMap<>();
    private final List<AuditEntry> audit = new ArrayList<>();

    public InternalRequestService(
            InternalRoleDirectory roles,
            TelegramBotClient telegram,
            @Value("${internal.approval-chat-id:}") String approvalChatId) {
        this(roles, telegram, approvalChatId, Clock.systemUTC());
    }

    InternalRequestService(
            InternalRoleDirectory roles, TelegramBotClient telegram, String approvalChatId, Clock clock) {
        this.roles = roles;
        this.telegram = telegram;
        this.approvalChatId = approvalChatId == null ? "" : approvalChatId.strip();
        this.clock = clock;
    }

    public synchronized Optional<String> handleMessage(
            String text, String chatType, String chatId, String userId, long updateId) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] command = text.strip().split("\\s+", 2);
        if (!Set.of("/internal-help", "/request", "/request-status").contains(command[0])) {
            return Optional.empty();
        }
        if (!"private".equals(chatType)) {
            return Optional.of("Internal requests are available only in a private chat with this bot.");
        }
        if (userId == null || userId.isBlank() || chatId == null || chatId.isBlank()) {
            return Optional.of("This request could not be associated with your private chat.");
        }
        Set<InternalRoleDirectory.Role> userRoles = roles.rolesFor(userId);
        if (userRoles.isEmpty()) {
            return Optional.of("This demo is limited to employees listed in the application's server-side identity configuration.");
        }
        return switch (command[0]) {
            case "/internal-help" -> Optional.of("Internal demo: the bot cannot start a private chat; open this chat and send a message first. Submit /request IT|short description, then use /request-status <reference>. Only synthetic categories and short details are accepted. A configured, server-side manager allowlist authorizes approval. This demo does not replace ITSM or employee identity systems.");
            case "/request" -> Optional.of(createRequest(
                    command.length == 2 ? command[1] : "", chatId, userId, updateId));
            case "/request-status" -> Optional.of(command.length == 2
                    ? status(command[1].strip(), chatId, userId, userRoles)
                    : "Use /request-status <reference>.");
            default -> Optional.empty();
        };
    }

    public synchronized CallbackResult handleCallback(String callbackChatId, String userId, String data) {
        if (!approvalChatId.equals(callbackChatId)) {
            return CallbackResult.denied("This action is not available in this chat.");
        }
        if (!roles.rolesFor(userId).contains(InternalRoleDirectory.Role.MANAGER)) {
            return CallbackResult.denied("Only an authorized manager can act on this request.");
        }
        String[] parts = data == null ? new String[0] : data.split("\\|", -1);
        if (parts.length == 3 && "employee_action".equals(parts[0])) {
            return selectAction(parts[1], parts[2], userId);
        }
        if (parts.length == 4 && "employee_confirm".equals(parts[0])) {
            return confirmAction(parts[1], parts[2], parts[3], userId);
        }
        if (parts.length == 3 && "employee_cancel".equals(parts[0])) {
            return cancelAction(parts[1], parts[2], userId);
        }
        return CallbackResult.denied("This request action is invalid.");
    }

    public synchronized List<AuditEntry> auditEntries() {
        return List.copyOf(audit);
    }

    private String createRequest(String input, String privateChatId, String employeeId, long updateId) {
        if (!roles.rolesFor(employeeId).stream().anyMatch(role -> role == InternalRoleDirectory.Role.EMPLOYEE
                || role == InternalRoleDirectory.Role.MANAGER)) {
            return "Your configured role does not allow employee requests.";
        }
        if (approvalChatId.isBlank()) {
            return "Manager routing is not configured. No request was created.";
        }
        String priorReference = requestsBySubmissionUpdate.get(updateId);
        if (priorReference != null) {
            Request priorRequest = requests.get(priorReference);
            if (priorRequest != null) {
                if (priorRequest.managerNotificationPending) {
                    sendManagerNotification(priorRequest);
                }
                return "Request already received. Reference: " + priorRequest.reference
                        + ". Status: " + priorRequest.status + ".";
            }
        }
        String[] fields = input.split("\\|", 2);
        String category = fields.length == 2 ? fields[0].strip().toUpperCase() : "";
        String description = fields.length == 2 ? fields[1].strip() : "";
        if (!CATEGORIES.contains(category) || description.isBlank() || description.length() > MAX_DESCRIPTION_LENGTH) {
            return "Use /request IT|short description. Allowed categories: IT, ACCESS, EQUIPMENT, OTHER; details are limited to 300 characters.";
        }
        String reference = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Request request = new Request(reference, employeeId, privateChatId, category, description);
        requests.put(reference, request);
        requestsBySubmissionUpdate.put(updateId, reference);
        if (requestsBySubmissionUpdate.size() > MAX_REQUESTS) {
            requestsBySubmissionUpdate.remove(requestsBySubmissionUpdate.keySet().iterator().next());
        }
        recordAudit(reference, employeeId, "submit", "PENDING");
        trimRequests();
        sendManagerNotification(request);
        return "Request created. Reference: " + reference + ". Status: PENDING. Check it with /request-status " + reference + ".";
    }

    private String status(
            String reference, String privateChatId, String userId, Set<InternalRoleDirectory.Role> userRoles) {
        Request request = requests.get(reference);
        if (request == null) {
            return "No request with that reference is available in this demo.";
        }
        boolean requester = request.employeeId.equals(userId) && request.employeeChatId.equals(privateChatId);
        boolean manager = userRoles.contains(InternalRoleDirectory.Role.MANAGER);
        if (!requester && !manager) {
            return "You are not authorized to view this request.";
        }
        return "Request " + request.reference + " status: " + request.status + ".";
    }

    private CallbackResult selectAction(String reference, String action, String managerId) {
        Request request = requests.get(reference);
        if (request == null || request.status != RequestStatus.PENDING) {
            return CallbackResult.denied("This request is no longer pending.");
        }
        if (!Set.of("approve", "reject").contains(action)) {
            return CallbackResult.denied("Choose approve or reject.");
        }
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        request.pendingManagerId = managerId;
        request.pendingAction = action;
        request.pendingToken = token;
        request.pendingUntil = clock.instant().plus(CONFIRMATION_TTL);
        List<List<TelegramBotClient.InlineButton>> keyboard = List.of(List.of(
                new TelegramBotClient.InlineButton("Confirm " + action, "employee_confirm|" + reference + "|" + action + "|" + token),
                new TelegramBotClient.InlineButton("Cancel", "employee_cancel|" + reference + "|" + token)));
        telegram.sendMessageWithInlineKeyboardOrLog(approvalChatId,
                "Confirm " + action + " for internal request " + reference + "? This action changes the request status.",
                keyboard);
        return new CallbackResult("Review the confirmation prompt before changing the request.", null, false);
    }

    private CallbackResult confirmAction(String reference, String action, String token, String managerId) {
        Request request = requests.get(reference);
        if (request == null || request.status != RequestStatus.PENDING) {
            return CallbackResult.denied("This request is no longer pending.");
        }
        if (!managerId.equals(request.pendingManagerId) || !action.equals(request.pendingAction)
                || !token.equals(request.pendingToken)) {
            return CallbackResult.denied("This confirmation is stale or belongs to another manager.");
        }
        if (request.pendingUntil == null || clock.instant().isAfter(request.pendingUntil)) {
            clearPending(request);
            return CallbackResult.denied("This confirmation expired. Choose an action again.");
        }
        request.status = "approve".equals(action) ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        recordAudit(request.reference, managerId, action, request.status.name());
        clearPending(request);
        String updatedMessage = "Internal request " + reference + " — " + request.status
                + " by an authorized manager at " + clock.instant() + ".";
        return new CallbackResult("Request " + request.status + ".", updatedMessage, true);
    }

    private CallbackResult cancelAction(String reference, String token, String managerId) {
        Request request = requests.get(reference);
        if (request == null || request.status != RequestStatus.PENDING
                || !managerId.equals(request.pendingManagerId) || !token.equals(request.pendingToken)) {
            return CallbackResult.denied("This confirmation is stale or unavailable.");
        }
        clearPending(request);
        return new CallbackResult("Confirmation cancelled. Request remains pending.", null, false);
    }

    private List<List<TelegramBotClient.InlineButton>> actionKeyboard(String reference) {
        return List.of(List.of(
                new TelegramBotClient.InlineButton("Review approve", "employee_action|" + reference + "|approve"),
                new TelegramBotClient.InlineButton("Review reject", "employee_action|" + reference + "|reject")));
    }

    private void sendManagerNotification(Request request) {
        String text = "Internal request " + request.reference + "\nCategory: " + request.category
                + "\nDetails: " + request.description + "\nStatus: PENDING.";
        telegram.sendMessageWithInlineKeyboardOrLog(approvalChatId, text, actionKeyboard(request.reference));
        request.category = null;
        request.description = null;
        request.managerNotificationPending = false;
    }

    private void recordAudit(String reference, String actor, String action, String outcome) {
        audit.add(new AuditEntry(reference, actor, action, clock.instant(), outcome));
        if (audit.size() > MAX_REQUESTS * 2) {
            audit.removeFirst();
        }
    }

    private void trimRequests() {
        if (requests.size() > MAX_REQUESTS) {
            String oldest = requests.keySet().iterator().next();
            requests.remove(oldest);
        }
    }

    private void clearPending(Request request) {
        request.pendingManagerId = null;
        request.pendingAction = null;
        request.pendingToken = null;
        request.pendingUntil = null;
    }

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    public record CallbackResult(String callbackText, String updatedMessage, boolean changed) {
        private static CallbackResult denied(String text) {
            return new CallbackResult(text, null, false);
        }
    }

    public record AuditEntry(String requestReference, String actorId, String action, Instant at, String outcome) {}

    private static final class Request {
        private final String reference;
        private final String employeeId;
        private final String employeeChatId;
        private String category;
        private String description;
        private boolean managerNotificationPending = true;
        private RequestStatus status = RequestStatus.PENDING;
        private String pendingManagerId;
        private String pendingAction;
        private String pendingToken;
        private Instant pendingUntil;

        private Request(String reference, String employeeId, String employeeChatId, String category, String description) {
            this.reference = reference;
            this.employeeId = employeeId;
            this.employeeChatId = employeeChatId;
            this.category = category;
            this.description = description;
        }
    }
}
