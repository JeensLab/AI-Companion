package dev.aicompanion.entity;

/** Acknowledgement from the server after a real action, rather than after dispatching a command. */
public record CompanionActionResult(boolean success, String message, Status status, Evidence evidence) {
    /** Facts of a completed server operation; ACCEPTED and speech carry no proof of completion. */
    public record Evidence(long workId, String action, String item, int count, java.util.UUID recipient,
                           java.util.UUID operation, java.util.UUID itemEntity) { }
    public enum Status { ACCEPTED, SUCCESS, FAILED, BLOCKED, TIMEOUT, CANCELLED,
        /** A craft refused at once for lack of materials (not a full bag, not a too long chain): the plan may get them instead. */
        SHORTAGE,
        /** A long smelt was handed to a furnace batch: the order is not done, the product is collected later. Never a success. */
        PARKED }
    public CompanionActionResult(boolean success, String message, Status status) { this(success, message, status, null); }
    public CompanionActionResult(boolean success, String message) { this(success, message, success ? Status.SUCCESS : Status.FAILED); }
    public CompanionActionResult {
        if (status == null || success != (status == Status.SUCCESS || status == Status.ACCEPTED))
            throw new IllegalArgumentException("Inconsistent action status");
    }
    public static CompanionActionResult ok(String message) { return new CompanionActionResult(true, message); }
    public static CompanionActionResult failed(String message) { return new CompanionActionResult(false, message); }
    public static CompanionActionResult accepted(String message) { return new CompanionActionResult(true, message, Status.ACCEPTED); }
    public static CompanionActionResult blocked(String message) { return new CompanionActionResult(false, message, Status.BLOCKED); }
    public static CompanionActionResult timeout(String message) { return new CompanionActionResult(false, message, Status.TIMEOUT); }
    public static CompanionActionResult shortage(String message) { return new CompanionActionResult(false, message, Status.SHORTAGE); }
    public static CompanionActionResult cancelled(String message) { return new CompanionActionResult(false, message, Status.CANCELLED); }
    public static CompanionActionResult parked(String message, Evidence facts) { return new CompanionActionResult(false, message, Status.PARKED, facts); }
    public CompanionActionResult withEvidence(Evidence facts) { return new CompanionActionResult(success, message, status, facts); }
    public net.minecraft.nbt.CompoundTag save() {
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("Status", status.name()); tag.putString("Message", message);
        if (evidence != null) {
            var e = new net.minecraft.nbt.CompoundTag(); e.putLong("WorkId", evidence.workId()); e.putString("Action", evidence.action());
            e.putString("Item", evidence.item()); e.putInt("Count", evidence.count());
            if (evidence.recipient() != null) e.putUUID("Recipient", evidence.recipient());
            if (evidence.operation() != null) e.putUUID("Operation", evidence.operation());
            if (evidence.itemEntity() != null) e.putUUID("ItemEntity", evidence.itemEntity());
            tag.put("Evidence", e);
        }
        return tag;
    }
    public static CompanionActionResult load(net.minecraft.nbt.CompoundTag tag) {
        try {
            var status = Status.valueOf(tag.getString("Status")); Evidence facts = null;
            if (tag.contains("Evidence")) {
                var e = tag.getCompound("Evidence");
                facts = new Evidence(e.getLong("WorkId"), e.getString("Action"), e.getString("Item"), e.getInt("Count"),
                        e.hasUUID("Recipient") ? e.getUUID("Recipient") : null, e.hasUUID("Operation") ? e.getUUID("Operation") : null,
                        e.hasUUID("ItemEntity") ? e.getUUID("ItemEntity") : null);
            }
            return new CompanionActionResult(status == Status.SUCCESS || status == Status.ACCEPTED, tag.getString("Message"), status, facts);
        } catch (RuntimeException ex) { return blocked("Сохранённый результат работы повреждён; успех не подтверждён."); }
    }
}
