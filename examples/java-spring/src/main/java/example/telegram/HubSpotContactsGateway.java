package example.telegram;

public interface HubSpotContactsGateway {
    String upsert(SalesLeadService.Contact contact);
}
