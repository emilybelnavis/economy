package ca.emilybelnavis.mcmods.economy.banking;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

/** Exercises the actual SavedData encoding and decoding without starting a world. */
public final class BankPersistenceTest {
    public static void main(String[] args) throws Exception {
        var constructor=BankData.class.getDeclaredConstructor(); constructor.setAccessible(true);
        var field=BankData.class.getDeclaredField("ledger"); field.setAccessible(true);
        var load=BankData.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class); load.setAccessible(true);
        BankData original=constructor.newInstance(); Ledger ledger=(Ledger)field.get(original);
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        ledger.credit(a,1000,"deposit"); ledger.transfer(a,b,250,"purchase"); ledger.debit(b,10,"withdraw");
        CompoundTag saved=original.save(new CompoundTag(),null);
        BankData restored=(BankData)load.invoke(null,saved.copy(),null); Ledger roundTrip=(Ledger)field.get(restored);
        assert roundTrip.balance(a)==750 && roundTrip.balance(b)==240;
        assert roundTrip.history(a).equals(ledger.history(a));
        assert roundTrip.history(b).equals(ledger.history(b));
        CompoundTag invalid=saved.copy(); invalid.putInt("Version",999);
        try {load.invoke(null,invalid,null);throw new AssertionError("Future data version accepted");}
        catch(java.lang.reflect.InvocationTargetException expected) {assert expected.getCause() instanceof IllegalStateException;}
        System.out.println("Bank persistence round-trip tests passed");
    }
}
