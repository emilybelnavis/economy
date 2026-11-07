package ca.emilybelnavis.mcmods.economy.banker.dialogue;
import ca.emilybelnavis.mcmods.economy.banker.StaffingRuntime;
import ca.emilybelnavis.mcmods.economy.banker.entity.BankerEntity;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
/** Question answers are disclosed only after an explicit offered selection. */
public final class BankerDialogue {
    private static final Map<String,String> QUESTIONS;
    static{var q=new LinkedHashMap<String,String>();q.put("opening","How do I open an account?");q.put("eligibility","Am I eligible?");q.put("institution","Which bank is this?");q.put("currency","What is the native currency?");q.put("basics","How does banking work?");QUESTIONS=Collections.unmodifiableMap(q);}
    private BankerDialogue(){}
    public static void open(ServerPlayer player,BankerEntity banker){
        if(StaffingRuntime.context(player,banker).isEmpty())return;
        UUID session=UUID.randomUUID();
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new BankerMenu(id,inventory,banker.getUUID(),banker.assignmentId(),banker.assignmentRevision(),session),Component.literal("Banker")),b->{b.writeUUID(banker.getUUID());b.writeUUID(banker.assignmentId());b.writeVarLong(banker.assignmentRevision());b.writeUUID(session);});
        if(player.containerMenu instanceof BankerMenu menu)menu.publish("questions",List.of(),choices());
    }
    private static List<BankerNetwork.Choice> choices(){var choices=new ArrayList<BankerNetwork.Choice>();QUESTIONS.forEach((id,label)->choices.add(new BankerNetwork.Choice("ask",id,label,false)));choices.add(new BankerNetwork.Choice("banking","","Open banking services",false));choices.add(new BankerNetwork.Choice("exit","","Goodbye",false));return List.copyOf(choices);}
    public static void action(ServerPlayer player,BankerMenu menu,String action,String value){
        if(!menu.valid(player)){player.closeContainer();return;}
        if(action.equals("exit")){player.closeContainer();return;}
        var banker=menu.entity(player);var context=StaffingRuntime.context(player,banker);if(context.isEmpty()){player.closeContainer();return;}
        if(action.equals("banking")){StaffingRuntime.openBanking(player,banker);return;}
        if(!action.equals("ask")||!QUESTIONS.containsKey(value))return;
        var c=context.get();String answer=switch(value){
            case "opening"->"Choose banking services, then review the account options offered by this bank. Opening an account requires the normal eligibility checks and your confirmation.";
            case "eligibility"->"Eligibility depends on this bank's current rules and your account status. Banking services will check your current details before offering an account.";
            case "institution"->"This branch represents "+ca.emilybelnavis.mcmods.economy.institution.InstitutionCatalog.require(new ca.emilybelnavis.mcmods.economy.institution.InstitutionCatalog.InstitutionId(c.institution())).name()+".";
            case "currency"->"This branch's native currency is "+c.currency()+". Review the currency and any quoted exchange terms before confirming a transaction.";
            case "basics"->"Banking services let you review eligible accounts and available actions. Check the amount, currency, fees and final confirmation. Talking to me alone does not open an account or move money.";
            default->throw new IllegalArgumentException("Question");};
        menu.publish("answer",List.of(QUESTIONS.get(value),answer),choices());
    }
}
