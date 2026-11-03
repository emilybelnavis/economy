package dev.emily.economy.recovery;

import dev.emily.economy.clock.EconomicRecord;
import dev.emily.economy.storage.StorageException;
import java.util.*;
import java.util.function.Consumer;

/** Explicit historical decoders. Missing types/versions fail closed; never silently skip facts. */
public final class RecordCatalog {
    public record Key(String type, int version) {}
    private final Map<Key, Consumer<String>> validators;
    public RecordCatalog(Map<Key, Consumer<String>> validators) { this.validators = Map.copyOf(validators); }
    public static RecordCatalog core() { return new RecordCatalog(Map.of(new Key(EconomicRecord.TYPE, 1), EconomicRecord::decode)); }
    public RecordCatalog with(Key key, Consumer<String> validator) {
        var copy = new HashMap<>(validators); if (copy.putIfAbsent(key, validator) != null) throw new IllegalArgumentException("Duplicate decoder");
        return new RecordCatalog(copy);
    }
    public void validate(String type, int version, String payload) {
        var decoder = validators.get(new Key(type, version));
        if (decoder == null) throw new StorageException(StorageException.Kind.SCHEMA, "Unknown committed record: " + type + " v" + version);
        try { decoder.accept(payload); }
        catch (RuntimeException e) { throw new StorageException(StorageException.Kind.CORRUPTION_IO, "Invalid committed record: " + type, e); }
    }
}
