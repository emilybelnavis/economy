package ca.emilybelnavis.mcmods.economy.foundation;

import ca.emilybelnavis.mcmods.economy.Economy;
import ca.emilybelnavis.mcmods.economy.institution.*;
import ca.emilybelnavis.mcmods.economy.account.AccountLifecycle;
import ca.emilybelnavis.mcmods.economy.account.AccountCatalog;
import ca.emilybelnavis.mcmods.economy.api.*;
import ca.emilybelnavis.mcmods.economy.api.Identifiers.*;
import ca.emilybelnavis.mcmods.economy.banking.BankingMenu;
import ca.emilybelnavis.mcmods.economy.banking.BankingNetwork;
import ca.emilybelnavis.mcmods.economy.banking.BankingController;
import ca.emilybelnavis.mcmods.economy.banking.BankingSaves;
import ca.emilybelnavis.mcmods.economy.cash.CashCatalog;
import ca.emilybelnavis.mcmods.economy.fx.FxQuotes;
import ca.emilybelnavis.mcmods.economy.journal.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Authenticated owner views and noncash banking actions. No transport field chooses the actor or world. */
public final class BankingAccounts {
    private static final String PENDING = "economy_banking_pending";
    private static final Set<String> ACTIONS = Set.of("setup", "accounts", "accounts_next", "manage_accounts", "account_select", "account_open", "account_close_preview", "account_close_confirm",
            "history", "history_next", "fx", "exchange_preview", "exchange_confirm", "settings", "kiosk_upgrade", "configure", "bank_retry", "branch_open", "branch_assign", "branch_switch", "branch_accept", "branch_close", "branch_products", "brand_preview", "brand_confirm", "cards", "card_select", "card_preview", "card_confirm", "card_lost", "credit_profile", "credit_history", "credit_next", "credit_facilities", "credit_facilities_next", "credit_facility", "credit_statements", "credit_statements_next");
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();
    private static final class State {
        AccountId cardAccount;
        ca.emilybelnavis.mcmods.economy.card.IssuedCards.Token cardToken;
        ca.emilybelnavis.mcmods.economy.card.IssuedCards.FeeQuote cardFee;
        LedgerService.BranchContext cardBranch;
        long creditAfter;
        Long creditNext;
        UUID creditFacilityNext;
        UUID creditFacility;
        EconomyCurrency creditCurrency;
        Long creditStatementNext;
        long cardPreviewRevision;
        LedgerService.BranchContext branchView;
        String pendingBrand;
        long brandRevision;
        AccountId selected;
        String accountCursor;
        String historyCursor;
        Map<UUID, AccountIdentity> offered = Map.of();
        Set<EconomyCurrency> openCurrencies = Set.of();
        TerminalPolicy.Snapshot fxTerminal;
        FxSettlement.Exchange exchange;
        FxQuotes.Quote quote;
        FundedClosure.Instruction closure;
        long closureRevision;
        long closureTerminalRevision;
        Function<PartyAuthorization.Grant, JournalService.Submission> retry;
    }
    private record Audience(ServerPlayer player, BankingMenu menu, UUID session, long revision) {
        boolean live() { return menu.valid(player) && menu.session().equals(session) && menu.revision() == revision; }
    }
    private BankingAccounts() {}
    public static boolean handles(String action) { return ACTIONS.contains(action); }
    public static AccountId selected(BankingMenu menu) { var state = STATES.get(menu); return state == null ? null : state.selected; }
    public static void closed(BankingMenu menu) { STATES.remove(menu); }

    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        if (!handles(action) || !menu.valid(player) || menu.busy()) return;
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > 2048) return;
        var services = Economy.serverServices(player.server);
        if (services == null || services.state() != ServerServices.State.READY) return;
        var grant = grant(player, services);
        if (grant == null) return;
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        var audience = new Audience(player, menu, menu.session(), menu.revision());
        if (Set.of("setup", "account_open", "account_close_preview", "account_close_confirm").contains(action) && audience.menu().access()!=BankingMenu.Access.KIOSK) {
            reply(audience, menu.page(), List.of("Account opening and closure require a bank branch.", "Visit the account institution's branded kiosk."), "Branch service unavailable");
            return;
        }
        try {
            switch (action) {
                case "credit_facilities", "credit_facilities_next" -> creditFacilities(audience,services,grant,state,action.equals("credit_facilities_next"));
                case "credit_facility" -> creditFacility(audience,services,grant,state,value);
                case "credit_statements", "credit_statements_next" -> creditStatements(audience,services,grant,state,action.equals("credit_statements_next"));
                case "credit_profile" -> creditProfile(audience,services,grant);
                case "credit_history", "credit_next" -> creditHistory(audience,services,grant,state,action.equals("credit_next"));
                case "cards" -> cards(audience,services,grant,state,"");
                case "card_select" -> {if(!offered(audience,action,value))throw new IllegalArgumentException("Card not offered");cards(audience,services,grant,state,value);}
                case "card_preview", "card_confirm", "card_lost" -> cardAction(audience,services,grant,state,action,value);
                case "setup" -> setup(audience, services, grant, state);
                case "accounts", "accounts_next" -> accounts(audience, services, grant, state, action.equals("accounts_next"));
                case "manage_accounts" -> {
                    if(menu.access()==BankingMenu.Access.KIOSK)branchHome(audience,services,grant,state);
                    else manage(audience,services,grant,state);
                }
                case "branch_open", "branch_assign", "branch_switch", "branch_accept", "branch_close", "branch_products" -> branchAction(audience,services,grant,state,action,value);
                case "brand_preview", "brand_confirm" -> brand(audience,services,grant,state,action,value);
                case "account_select" -> select(audience, services, grant, state, value);
                case "account_open" -> open(audience, services, grant, state, value);
                case "account_close_preview" -> closePreview(audience, services, grant, state, value);
                case "account_close_confirm" -> closeConfirm(audience, services, grant, state);
                case "history", "history_next" -> history(audience, services, grant, state, action.equals("history_next"));
                case "fx" -> fx(audience, services, grant, state);
                case "exchange_preview" -> exchangePreview(audience, services, grant, state, value);
                case "exchange_confirm" -> exchangeConfirm(audience, services, grant, state);
                case "kiosk_upgrade" -> {
                    if(audience.menu().access()!=BankingMenu.Access.ADMIN){unavailable(audience,"settings");return;}
                    audience.menu().setBusy(true);
                    ca.emilybelnavis.mcmods.economy.terminal.KioskUpgradeController.request(audience.player(),
                            new net.minecraft.core.BlockPos(audience.menu().anchor().x(),audience.menu().anchor().y(),audience.menu().anchor().z()),
                            message->{if(audience.player().server.getPlayerList().getPlayer(audience.player().getUUID())==audience.player())audience.player().displayClientMessage(net.minecraft.network.chat.Component.literal(message),false);});
                    audience.player().closeContainer();
                }
                case "settings" -> settings(audience, services);
                case "configure" -> configure(audience, services, grant, state, value);
                case "bank_retry" -> retry(audience, services, grant, state);
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            reply(audience, menu.page(), List.of("The request was not submitted. Check the values and current terminal."), "Invalid banking request");
        }
    }
    private static void creditProfile(Audience audience,ServerServices services,PartyAuthorization.Grant grant) {
        audience.menu().setBusy(true);
        services.ledger().creditProfile(grant).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"credit_profile");return;}
            var view=found.orElseThrow();var lines=new ArrayList<String>();
            if(view.profile().isEmpty()) {
                lines.add("Credit is not established.");
                lines.add("A debit card, login or failed application does not create a credit profile.");
                lines.add("A first actual credit-card issuance establishes credit.");
            } else {
                var profile=view.profile().orElseThrow();
                lines.add("Saved credit score: "+profile.score()+" (300–900)");
                lines.add("First credit issuance: "+java.time.Instant.ofEpochMilli(profile.firstIssuedAt()));
                view.latest().ifPresent(history->{
                    var c=history.components();
                    lines.add("Baseline: "+c.baseline()+"; payment history: "+signed(c.payment()));
                    lines.add("Utilization: "+signed(c.utilization())+"; credit age: "+signed(c.age()));
                    lines.add("Inquiries: -"+c.inquiry()+"; defaults: -"+c.defaults());
                    lines.add("Recorded at: "+java.time.Instant.ofEpochMilli(history.boundary()));
                });
            }
            lines.add("Scheduled score updates await the reporting service.");
            reply(audience,"credit_profile",lines,"Credit profile",List.of(new BankingNetwork.Choice("credit_history","","Score history",false),new BankingNetwork.Choice("credit_facilities","","Credit facilities",false)));
        });
    }
    private static String signed(int value){return (value>=0?"+":"")+value;}
    private static void creditHistory(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,boolean next) {
        if(next && (state.creditNext==null || !offered(audience,"credit_next","")))throw new IllegalArgumentException("No credit history page offered");
        state.creditAfter=next?state.creditNext:0;audience.menu().setBusy(true);
        services.ledger().creditHistory(grant,state.creditAfter,8).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"credit_history");return;}
            var page=found.orElseThrow();state.creditNext=page.nextCursor();
            var lines=new ArrayList<String>();
            for(var entry:page.entries()) {
                lines.add(java.time.Instant.ofEpochMilli(entry.boundary())+" — score "+entry.score());
                var c=entry.components();
                lines.add("Payment "+signed(c.payment())+", utilization "+signed(c.utilization())+", age "+signed(c.age())+", inquiries -"+c.inquiry()+", defaults -"+c.defaults());
            }
            if(lines.isEmpty())lines.add("No credit history. Debit issuance does not establish credit.");
            var choices=new ArrayList<BankingNetwork.Choice>();choices.add(new BankingNetwork.Choice("credit_profile","","Back to profile",false));
            if(state.creditNext!=null)choices.add(new BankingNetwork.Choice("credit_next","","Next history page",false));
            reply(audience,"credit_history",lines,"Saved credit history",choices);
        });
    }

    private static void creditFacilities(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,boolean next) {
        if(next && (state.creditFacilityNext==null || !offered(audience,"credit_facilities_next","")))throw new IllegalArgumentException("No facility page offered");
        UUID after=next?state.creditFacilityNext:null;
        audience.menu().setBusy(true);
        services.ledger().creditFacilityPage(grant,after,8).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"credit_facilities");return;}
            var page=found.orElseThrow();state.creditFacilityNext=page.nextCursor();
            var choices=new ArrayList<BankingNetwork.Choice>();
            for(var view:page.entries()) {
                var bank=InstitutionCatalog.require(new InstitutionCatalog.InstitutionId(view.issuer()));
                choices.add(new BankingNetwork.Choice("credit_facility",view.id().toString(),bank.name()+" · "+view.network()+" · "+view.currency().code(),false));
            }
            if(state.creditFacilityNext!=null)choices.add(new BankingNetwork.Choice("credit_facilities_next","","Next facilities",false));
            reply(audience,"credit_facilities",List.of(choices.isEmpty()?"You have no credit facilities.":"Select a facility to view balances and statements."),"Credit facilities",choices);
        });
    }
    private static void creditFacility(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,String value) {
        if(!offered(audience,"credit_facility",value))throw new IllegalArgumentException("Facility not offered");
        var id=UUID.fromString(value);audience.menu().setBusy(true);
        services.ledger().creditFacilityView(grant,id).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"credit_facility");return;}
            state.creditFacility=id;state.creditCurrency=found.orElseThrow().currency();state.creditStatementNext=null;
            reply(audience,"credit_facility",CreditReport.owner(found.orElseThrow()),"Credit facility",List.of(
                    new BankingNetwork.Choice("credit_facilities","","Back to facilities",false),
                    new BankingNetwork.Choice("credit_statements","","Statements",false)));
        });
    }
    private static void creditStatements(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,boolean next) {
        if(state.creditFacility==null || !offered(audience,next?"credit_statements_next":"credit_statements",""))throw new IllegalArgumentException("Statement page not offered");
        if(next && state.creditStatementNext==null)throw new IllegalArgumentException("No statement page offered");
        long after=next?state.creditStatementNext:0;audience.menu().setBusy(true);
        services.ledger().creditStatements(grant,state.creditFacility,after,2).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"credit_statements");return;}
            var page=found.orElseThrow();state.creditStatementNext=page.nextClosedAt();
            var lines=new ArrayList<String>();
            for(var statement:page.statements())lines.addAll(CreditReport.statement(statement,state.creditCurrency));
            if(lines.isEmpty())lines.add("No statements have closed for this facility.");
            var choices=new ArrayList<BankingNetwork.Choice>();
            choices.add(new BankingNetwork.Choice("credit_facility",state.creditFacility.toString(),"Back to facility",false));
            if(state.creditStatementNext!=null)choices.add(new BankingNetwork.Choice("credit_statements_next","","Next statements",false));
            reply(audience,"credit_statements",lines,"Frozen statements",choices);
        });
    }

    private static void cards(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,String selected) {
        audience.menu().setBusy(true);state.cardFee=null;state.cardBranch=null;
        ownerAccounts(services,grant).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"cards");return;}
            state.offered=offeredAccounts(found.orElseThrow().accounts());
            if(state.offered.isEmpty()){reply(audience,"cards",List.of("Open and assign an account at its bank kiosk first."),"No issued debit account",List.of());return;}
            var id=selected.isBlank()?state.offered.keySet().iterator().next():UUID.fromString(selected);
            var account=state.offered.get(id);if(account==null){unavailable(audience,"cards");return;}
            state.cardAccount=account.id();
            services.ledger().branchContext(grant,audience.menu().anchor(),account.id()).thenCombine(services.ledger().accountCard(grant,account.id()),Map::entry).whenComplete((result,failure)->{
                if(!audience.live())return;
                if(failure!=null || result.getKey().isEmpty()){unavailable(audience,"cards");return;}
                var view=result.getKey().orElseThrow();var provider=view.provider();
                var token=result.getValue().filter(t->provider!=null && t.providerEpoch()==provider.debitEpoch() && t.issuer().key().equals(provider.bankKey())).orElse(null);
                state.cardToken=token;
                var choices=new ArrayList<BankingNetwork.Choice>();
                for(var a:state.offered.values())choices.add(new BankingNetwork.Choice("card_select",a.id().value().toString(),a.currency().code()+(a.primary()?" primary":" account"),a.id().equals(account.id())));
                var lines=new ArrayList<String>();lines.add("Debit card for "+account.currency().code()+" account");
                if(provider==null || provider.legacy() && provider.bankKey().equals(BankProviders.UNASSIGNED_PROVIDER))lines.set(0,"Unassigned account: visit a bank kiosk before issuance.");
                else {
                    lines.add(InstitutionCatalog.require(new InstitutionCatalog.InstitutionId(provider.bankKey())).name());
                    if(token!=null) {
                        lines.add("Status: "+(System.currentTimeMillis()>=token.expiresAt()?"EXPIRED":token.status()));
                        lines.add("Expires UTC: "+java.time.Instant.ofEpochMilli(token.expiresAt()));
                        if(token.status()==ca.emilybelnavis.mcmods.economy.card.IssuedCards.Status.ACTIVE && System.currentTimeMillis()<token.expiresAt())
                            choices.add(new BankingNetwork.Choice("card_lost",account.id().value().toString(),"Report card lost (free)",false));
                    } else lines.add("First debit card: free. No credit or score is created.");
                    String issuerName=InstitutionCatalog.require(new InstitutionCatalog.InstitutionId(provider.bankKey())).name();
                    lines.set(0,token==null?issuerName+" — first debit card free":issuerName+" — "+ca.emilybelnavis.mcmods.economy.card.IssuedCards.effectiveStatus(token,System.currentTimeMillis())
                            +" — "+java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(token.expiresAt())));
                    boolean issuerKiosk=audience.menu().access()==BankingMenu.Access.KIOSK && view.terminal()!=null && view.terminal().binding()!=null
                            && view.terminal().binding().bound() && view.terminal().binding().institution().key().equals(provider.bankKey());
                    if(issuerKiosk)choices.add(new BankingNetwork.Choice("card_preview",account.id().value().toString(),token==null?"Review free issuance":"Review replacement / renewal",false));
                    else lines.add("Issuance and replacement require this issuer's kiosk.");
                }
                lines.add("Use Collect items for committed card deliveries.");
                choices.add(new BankingNetwork.Choice("claims","","Collect issued cards / protected items",false));
                reply(audience,"cards",lines,"Issued debit cards",choices);
            });
        });
    }
    private static void cardAction(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,String action,String value) {
        if(!offered(audience,action,value) || state.cardAccount==null)throw new IllegalArgumentException("Choose a server-offered card action");
        if(action.equals("card_lost")) {
            var token=state.cardToken;if(token==null || !value.equals(state.cardAccount.value().toString()))throw new IllegalArgumentException("Card selection stale");
            var operation=new OperationId(UUID.randomUUID());var attempt=new RequestId(UUID.randomUUID());
            var request=ca.emilybelnavis.mcmods.economy.card.IssuedCards.revokeRequest(token.id(),token.revision(),new PlayerId(audience.player().getUUID()));
            start(audience,services,grant,state,new Journal.Intent(operation,attempt,grant.actor(),ca.emilybelnavis.mcmods.economy.card.IssuedCards.REVOKE,request,null,null),
                    fresh->services.ledger().revokeCard(fresh,operation,attempt,token.id(),token.revision()),"cards");return;
        }
        if(audience.menu().access()!=BankingMenu.Access.KIOSK)throw new IllegalArgumentException("Issuer kiosk required");
        if(action.equals("card_confirm")) {
            if(!audience.menu().page().equals("card_review") || audience.menu().revision()!=state.cardPreviewRevision || state.cardBranch==null)throw new IllegalArgumentException("Review card first");
            var view=state.cardBranch;var old=state.cardToken;
            var lease=proof(audience,grant,view,old==null?BranchAdmission.Action.CARD_ISSUE:BranchAdmission.Action.CARD_REPLACE,null);
            var request=new ca.emilybelnavis.mcmods.economy.card.IssuedCards.Request(lease,view.tier().revision(),old==null?null:old.id(),state.cardFee);
            var operation=new OperationId(UUID.randomUUID());var attempt=new RequestId(UUID.randomUUID());
            start(audience,services,grant,state,new Journal.Intent(operation,attempt,grant.actor(),ca.emilybelnavis.mcmods.economy.card.IssuedCards.ISSUE,request.request(),null,null),
                    fresh->services.ledger().issueCard(fresh,operation,attempt,request),"cards");state.cardBranch=null;state.cardFee=null;return;
        }
        String[] parts=value.split("\\|",-1);
        if(!parts[0].equals(state.cardAccount.value().toString()))throw new IllegalArgumentException("Card selection changed");
        var old=state.cardToken;
        boolean paid=old!=null && !ca.emilybelnavis.mcmods.economy.card.IssuedCards.freeRenewal(old,System.currentTimeMillis());
        if(paid && parts.length==1) {
            var choices=state.offered.values().stream().map(a->new BankingNetwork.Choice("card_preview",value+"|"+a.id().value(),"Pay from "+a.currency().code()+" account",false)).toList();
            reply(audience,"card_funding",List.of("Replacement costs 25 in the world's currency.","Choose a funding account, then review the combined debit and FX fee."),"Choose funding account",choices);return;
        }
        audience.menu().setBusy(true);
        var funding=paid?new AccountId(UUID.fromString(parts[1])):null;
        if(paid && !state.offered.containsKey(funding.value()))throw new IllegalArgumentException("Funding not offered");
        services.ledger().branchContext(grant,audience.menu().anchor(),state.cardAccount).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"cards");return;}
            var view=found.orElseThrow();
            if(view.provider()==null || view.terminal()==null || view.terminal().binding()==null || !view.terminal().binding().bound()
                    || !view.provider().bankKey().equals(view.terminal().binding().institution().key())) {
                reply(audience,"cards",List.of("Visit the card account's issuing bank kiosk."),"Wrong issuer kiosk",List.of());return;
            }
            java.util.concurrent.CompletionStage<ca.emilybelnavis.mcmods.economy.card.IssuedCards.FeeQuote> quoted=paid
                    ?services.ledger().quoteCardReplacement(grant,state.cardAccount,funding):java.util.concurrent.CompletableFuture.completedFuture(null);
            quoted.whenComplete((fee,failure)->{
                if(!audience.live())return;
                if(failure!=null){reply(audience,"cards",List.of("A fresh fee preview is unavailable. No card was revoked or charged."),"Fee quote unavailable",List.of());return;}
                state.cardBranch=view;state.cardFee=fee;
                var lines=new ArrayList<String>();lines.add(old==null?"Issue the first debit token for this account?":"Replace the current token and revoke all its copies?");
                lines.add("Issuer: "+InstitutionCatalog.require(view.terminal().binding().institution()).name());
                lines.add(fee==null?"Fee: free":"Combined account debit: "+money(new Money(fee.currency(),fee.debitMinor())));
                if(fee!=null) {
                    var amounts=fee.capture().split("\\|",-1);
                    lines.add("Replacement principal: "+money(new Money(fee.currency(),Long.parseLong(amounts[4]))));
                    lines.add("FX fee: "+money(new Money(fee.currency(),Long.parseLong(amounts[5]))));
                }
                lines.add("Expiry: five financial years from successful issuance.");lines.add("Physical delivery uses Collect items; uncertain delivery never charges again.");
                reply(audience,"card_review",lines,"Confirm debit card",List.of(new BankingNetwork.Choice("card_confirm","","Confirm issuance",false)));
                state.cardPreviewRevision=audience.menu().revision();
            });
        });
    }

    private static void branchHome(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        audience.menu().setBusy(true);
        services.ledger().branchContext(grant, audience.menu().anchor(), null).thenCombine(ownerAccounts(services, grant), Map::entry)
                .whenComplete((result, error) -> {
            if (!audience.live()) return;
            if (error != null || result.getKey().isEmpty() || result.getValue().isEmpty()) {
                if (error != null) com.mojang.logging.LogUtils.getLogger().warn("event=BANKING_BRANCH_READ_FAILED session={} reason={}", audience.session(), ServerServices.failureCode(error));
                unavailable(audience,"branch"); return;
            }
            var context=result.getKey().orElseThrow(); var terminal=context.terminal();
            if (terminal==null || terminal.binding()==null || !terminal.binding().bound()) {
                reply(audience,"branch",List.of("This kiosk has no bank binding.","Owner: sneak-right-click with an empty hand to configure it."),"Bank selection required",List.of()); return;
            }
            var bank=InstitutionCatalog.require(terminal.binding().institution());
            state.offered=offeredAccounts(result.getValue().orElseThrow().accounts());
            var choices=new ArrayList<BankingNetwork.Choice>();
            choices.add(new BankingNetwork.Choice("branch_open","",context.primary()==null?"Open primary account":"Open "+bank.baseCurrency().code()+" account",false));
            for(var account:state.offered.values()) {
                String id=account.id().value().toString(); String label=account.currency().code()+(account.primary()?" primary":" account");
                choices.add(new BankingNetwork.Choice("branch_assign",id,"Assign "+label+" here",false));
                choices.add(new BankingNetwork.Choice("branch_switch",id,"Switch provider: "+label,false));
                choices.add(new BankingNetwork.Choice("branch_accept",id,"Confirm switch here: "+label,false));
                if(!account.primary())choices.add(new BankingNetwork.Choice("branch_close",id,"Close "+label,false));
            }
            choices.add(new BankingNetwork.Choice("cards","","Issued debit cards",false));
            choices.add(new BankingNetwork.Choice("credit_profile","","Credit profile",false));
            choices.add(new BankingNetwork.Choice("credit_facilities","","Credit facilities",false));
            choices.add(new BankingNetwork.Choice("finance_home", "", "Bank and credit services", false));
            choices.add(new BankingNetwork.Choice("branch_products","","Cards and applications",false));
            reply(audience,"branch",List.of(bank.name()+" — "+bank.baseCurrency().code(),"Four open accounts total, including primary.",
                    "Assignment and closure require the account's bank. Switching requires both banks."),"Branch services",choices);
        });
    }
    private static boolean offered(Audience audience,String action,String value) {
        return audience.menu().choices().stream().anyMatch(c->c.action().equals(action)&&c.value().equals(value));
    }
    private static BranchAdmission.Proof proof(Audience audience, PartyAuthorization.Grant grant,
            LedgerService.BranchContext view, BranchAdmission.Action action, InstitutionCatalog.InstitutionId target) {
        var terminal=view.terminal();
        if(terminal==null || terminal.binding()==null || !terminal.binding().bound() || view.denomination()==null)throw new IllegalArgumentException("BRANCH_UNAVAILABLE");
        var account=action==BranchAdmission.Action.SECONDARY?view.primary():view.account();
        var context=new BranchAdmission.Context(grant.target().world(),audience.menu().session(),new PlayerId(audience.player().getUUID()),action,
                terminal.binding().institution(),target,account==null?null:account.id(),account==null?0:account.revision(),
                view.provider()==null?0:view.provider().revision(),view.denomination().revision());
        return KioskAdmission.issue(audience.player(),audience.menu(),terminal,context);
    }
    private static void branchAction(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,String action,String value) {
        if(audience.menu().access()!=BankingMenu.Access.KIOSK || !offered(audience,action,value))throw new IllegalArgumentException("Choose an offered branch action");
        if(action.equals("branch_products")) {
            reply(audience,"branch_products",List.of("Issuer applications and card services require this live branch.",
                    "Review current terms before confirming. Trading and collateral lending are unavailable."),"Branch services",List.of(
                    new BankingNetwork.Choice("cards", "", "Issued debit cards", false),
                    new BankingNetwork.Choice("finance_credit_apply", "", "Credit application", false),
                    new BankingNetwork.Choice("finance_credit_cards", "", "Credit tokens and replacement", false),
                    new BankingNetwork.Choice("finance_credit_raise", "", "Credit limit increase", false),
                    new BankingNetwork.Choice("finance_loan_apply", "", "Loan application", false),
                    new BankingNetwork.Choice("finance_home", "", "All financial services", false)));return;
        }
        String[] fields=value.split("\\|",-1);
        AccountId selected=action.equals("branch_open")?null:new AccountId(UUID.fromString(fields[0]));
        if(selected!=null && !state.offered.containsKey(selected.value()))throw new IllegalArgumentException("Account not offered");
        audience.menu().setBusy(true);
        services.ledger().branchContext(grant,audience.menu().anchor(),selected).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"branch");return;}
            try {
                var view=found.orElseThrow();var terminal=view.terminal();
                if(terminal==null || terminal.binding()==null || !terminal.binding().bound())throw new IllegalArgumentException("BRANCH_UNAVAILABLE");
                var bank=InstitutionCatalog.require(terminal.binding().institution());
                if(action.equals("branch_close")) {
                    if(view.provider()==null || !view.provider().bankKey().equals(bank.id().key()))throw new IllegalArgumentException("Wrong account bank");
                    state.branchView=view;
                    audience.menu().setBusy(false);
                    closePreview(audience,services,grant,state,value);return;
                }
                if(action.equals("branch_switch") && fields.length==1) {
                    if(view.account()==null || view.provider()==null || !view.provider().bankKey().equals(bank.id().key()))throw new IllegalArgumentException("Visit the source bank");
                    var choices=InstitutionCatalog.banks().stream().filter(b->b.baseCurrency()==view.account().currency() && !b.id().equals(bank.id()))
                            .map(b->new BankingNetwork.Choice("branch_switch",value+"|"+b.id().key(),"Switch to "+b.name(),false)).toList();
                    reply(audience,"branch_targets",List.of("Authorize one target bank. Then visit that target within seven financial days."),"Choose target bank",choices);return;
                }
                var branchAction=switch(action) {
                    case "branch_open"->view.primary()==null?BranchAdmission.Action.PRIMARY:BranchAdmission.Action.SECONDARY;
                    case "branch_assign"->BranchAdmission.Action.ASSIGN;
                    case "branch_accept"->BranchAdmission.Action.SWITCH_TARGET;
                    case "branch_switch"->BranchAdmission.Action.SWITCH_SOURCE;
                    default->throw new IllegalArgumentException("Branch action");
                };
                var target=fields.length==2?new InstitutionCatalog.InstitutionId(fields[1]):null;
                var lease=proof(audience,grant,view,branchAction,target);
                var request=new BankProviders.Request(lease,view.tier().revision());
                var kind=switch(branchAction){case PRIMARY->BankProviders.PRIMARY;case SECONDARY->BankProviders.SECONDARY;
                    case ASSIGN->BankProviders.ASSIGN;case SWITCH_SOURCE->BankProviders.SWITCH_SOURCE;case SWITCH_TARGET->BankProviders.SWITCH_TARGET;default->throw new IllegalArgumentException();};
                var operation=new OperationId(UUID.randomUUID());var attempt=new RequestId(UUID.randomUUID());
                var intent=new Journal.Intent(operation,attempt,grant.actor(),kind,request.request(),null,null);
                start(audience,services,grant,state,intent,fresh->services.bankLifecycle(fresh,operation,attempt,request,null),"branch");
            } catch(IllegalArgumentException | IllegalStateException invalid) {
                reply(audience,"branch",List.of("Visit the correct bank and refresh the account before trying again."),"Branch admission unavailable",List.of());
            }
        });
    }

    private static PartyAuthorization.Grant grant(ServerPlayer player, ServerServices services) {
        var world = services.partyWorld();
        if (world == null) return null;
        var target = new PartyRef(world, new PartyRef.Player(new PlayerId(player.getUUID())));
        return PartyAuthorization.player(player, Economy::serverServices, target).orElse(null);
    }
    private static void setup(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (!audience.menu().page().equals("accounts") || audience.menu().choices().stream().noneMatch(choice -> choice.action().equals("setup")))
            throw new IllegalArgumentException("Open account from My accounts");
        var request = new Journal.CanonicalRequest(1, Map.of());
        var op = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        var kind = ca.emilybelnavis.mcmods.economy.account.AccountCatalog.INITIALIZE_PRIMARY;
        start(audience, services, grant, state, ownedIntent(grant, kind, op, attempt, request),
                fresh -> services.initializePrimary(fresh, op, attempt, null), "accounts");
    }
    private record OwnerAccounts(List<AccountIdentity> accounts, EconomyCurrency worldCurrency,
                                 Set<EconomyCurrency> availableCurrencies) {}
    /** Query open identities directly so retained closed history never hides an active account. */
    private static java.util.concurrent.CompletionStage<Optional<OwnerAccounts>> ownerAccounts(ServerServices services, PartyAuthorization.Grant grant) {
        return services.readAccounts(grant, false, reads -> {
            var player = (PartyRef.Player) grant.target().owner();
            var ids = reads.query("SELECT account_id FROM account_catalog WHERE owner_id=? AND purpose='BANK' AND status='OPEN' ORDER BY account_id LIMIT 4",
                    List.of(player.id().value().toString()), 4);
            var accounts = ids.stream().map(row -> AccountCatalog.find(reads, grant.target(),
                    new AccountId(UUID.fromString(row.get("account_id")))).orElseThrow()).toList();
            var denomination = RebaseSettlement.denomination(reads).map(value -> value.currency()).orElse(null);
            return Optional.of(new OwnerAccounts(accounts, denomination, Set.copyOf(ca.emilybelnavis.mcmods.economy.fx.FxService.availableCurrencies(reads))));
        });
    }
    private static void accounts(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, boolean next) {
        audience.menu().setBusy(true); state.openCurrencies = Set.of(); state.closure = null;
        ownerAccounts(services, grant).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience, "accounts"); return; }
            state.offered = offeredAccounts(found.orElseThrow().accounts());
            state.accountCursor = null;
            var choices = accountChoices(state, "history");
            if (choices.isEmpty() && audience.menu().access()==BankingMenu.Access.KIOSK) choices.add(new BankingNetwork.Choice("manage_accounts", "", "Visit branch services", false));
            else choices.add(new BankingNetwork.Choice("manage_accounts", "", "Manage account", false));
            reply(audience, "accounts", List.of(), choices.size() == 1 && state.offered.isEmpty() ? "Visit a bank kiosk to open your primary account." : "Choose an account to view its transactions.", choices);
        });
    }
    static Set<EconomyCurrency> availableCurrencies(List<AccountIdentity> accounts) {
        var banks = accounts.stream().filter(account -> account.purpose() == AccountIdentity.Purpose.BANK && account.status() == AccountIdentity.Status.OPEN).toList();
        if (banks.size() >= AccountIdentity.MAX_OPEN_BANK_ACCOUNTS) return Set.of();
        var available = EnumSet.allOf(EconomyCurrency.class);
        banks.forEach(account -> available.remove(account.currency()));
        return Collections.unmodifiableSet(available);
    }
    static Set<EconomyCurrency> availableCurrencies(List<AccountIdentity> accounts, Set<EconomyCurrency> published) {
        var available = EnumSet.noneOf(EconomyCurrency.class);
        available.addAll(availableCurrencies(accounts));
        available.retainAll(published);
        return Collections.unmodifiableSet(available);
    }
    static boolean mayClose(AccountIdentity account, EconomyCurrency worldCurrency, AccountFunds funds, boolean teller) {
        return teller && account.purpose() == AccountIdentity.Purpose.BANK && account.status() == AccountIdentity.Status.OPEN
                && !account.primary() && account.currency() != worldCurrency
                && funds.reservedMinorUnits() == 0 && funds.restrictedMinorUnits() == 0;
    }
    private static void manage(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        state.closure = null; state.openCurrencies = Set.of();
        audience.menu().setBusy(true);
        ownerAccounts(services, grant).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience, "manage"); return; }
            var snapshot = found.orElseThrow();
            state.offered = offeredAccounts(snapshot.accounts());
            manageBalances(audience, services, grant, state, snapshot, List.copyOf(state.offered.values()), 0, new ArrayList<>());
        });
    }
    private static void manageBalances(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state,
                                       OwnerAccounts snapshot, List<AccountIdentity> accounts, int index, List<BankingNetwork.Choice> rows) {
        boolean teller = audience.menu().access() == BankingMenu.Access.TELLER;
        if (index == accounts.size()) {
            state.openCurrencies = Set.of();
            for (var currency : EconomyCurrency.all()) if (state.openCurrencies.contains(currency))
                rows.add(new BankingNetwork.Choice("account_open", currency.code(), currency.code(), false));
            String status = "Visit the account institution's branch kiosk for lifecycle changes. Account limit: 4.";
            reply(audience, "manage", List.of(), status, rows);
            return;
        }
        var account = accounts.get(index);
        services.ledger().funds(grant, account.id()).whenComplete((funds, error) -> {
            if (!audience.live()) return;
            if (error != null || funds.isEmpty()) { unavailable(audience, "manage"); return; }
            var balance = funds.orElseThrow();
            String label = account.currency().code() + (account.primary() ? " primary" : " account") + " — " + money(balance.posted());
            rows.add(new BankingNetwork.Choice("account_close_preview", account.id().value().toString(), label,
                    false));
            manageBalances(audience, services, grant, state, snapshot, accounts, index + 1, rows);
        });
    }
    private static Map<UUID, AccountIdentity> offeredAccounts(List<AccountIdentity> accounts) {
        var offered = new LinkedHashMap<UUID, AccountIdentity>();
        for (var account : accounts) if (account.purpose() == AccountIdentity.Purpose.BANK && account.status() == AccountIdentity.Status.OPEN)
            offered.put(account.id().value(), account);
        return Collections.unmodifiableMap(offered);
    }
    private static List<BankingNetwork.Choice> accountChoices(State state, String page) {
        var choices = new ArrayList<BankingNetwork.Choice>();
        for (var account : state.offered.values()) choices.add(new BankingNetwork.Choice("account_select",
                account.id().value() + "|" + page, account.currency().code() + (account.primary() ? " primary account" : " account"), account.id().equals(state.selected)));
        return choices;
    }
    /** Offers only authenticated owner accounts; the selected account determines the transaction currency. */
    public static void cashAccounts(ServerPlayer player, BankingMenu menu, String page, List<String> lines, String status) {
        var services = Economy.serverServices(player.server);
        if (services == null || !menu.valid(player)) return;
        var grant = grant(player, services);
        if (grant == null) return;
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        var audience = new Audience(player, menu, menu.session(), menu.revision());
        menu.setBusy(true);
        services.ledger().bankingContext(new PlayerId(player.getUUID()), menu.anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null) { unavailable(audience, page); return; }
            state.selected = context.account().id();
            ownerAccounts(services, grant).whenComplete((found, accountError) -> {
                if (!audience.live()) return;
                if (accountError != null || found.isEmpty()) { unavailable(audience, page); return; }
                state.offered = offeredAccounts(found.orElseThrow().accounts());
                var display = new ArrayList<String>();
                display.add("Selected: " + context.account().currency().code() + " account");
                display.addAll(lines);
                if(context.terminal()!=null && context.terminal().binding()!=null && context.terminal().binding().bound())
                    display.add("Terminal cash currency: "+context.terminal().binding().nativeCurrency().code()+"; account currency: "+context.account().currency().code());
                String pageStatus = page.equals("deposit") && context.terminal() != null && context.terminal().kind() == TerminalPolicy.Kind.ATM
                        && !context.terminal().settings().mode().deposits()
                        ? "Deposits disabled on this ATM. Its owner can enable them." : status;
                reply(audience, page, display, pageStatus, accountChoices(state, page));
            });
        });
    }
    private static AccountIdentity offered(State state, String value) {
        var text = value.trim();
        if (text.matches("[1-9][0-9]?")) {
            int index = Integer.parseInt(text) - 1;
            var accounts = List.copyOf(state.offered.values());
            if (index >= accounts.size()) throw new IllegalArgumentException("Account selection");
            return accounts.get(index);
        }
        var account = state.offered.get(UUID.fromString(text));
        if (account == null) throw new IllegalArgumentException("Account not offered");
        return account;
    }
    private static void select(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var fields = value.split("\\|", -1);
        if (fields.length > 2) throw new IllegalArgumentException("Account selection");
        String page = fields.length == 2 ? fields[1] : "history";
        if (!Set.of("history", "deposit", "withdraw", "fx").contains(page)
                || (!audience.menu().page().equals("accounts") && !audience.menu().page().equals(page)))
            throw new IllegalArgumentException("Account selection page");
        if (!BankingController.canChangeAccount(audience.menu())) throw new IllegalStateException("Resolve the current cash transaction first.");
        var selected = offered(state, fields[0]);
        audience.menu().setBusy(true);
        services.findOwnedAccount(grant, selected.id()).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty() || found.orElseThrow().status() != AccountIdentity.Status.OPEN) { unavailable(audience, page); return; }
            if (!BankingController.canChangeAccount(audience.menu())) {
                reply(audience, audience.menu().page(), audience.menu().lines(), "Resolve the current cash transaction first."); return;
            }
            state.selected = selected.id(); state.historyCursor = null; state.exchange = null; state.quote = null; state.closure = null;
            audience.menu().setBusy(false);
            switch (page) {
                case "history" -> history(audience, services, grant, state, false);
                case "fx" -> fx(audience, services, grant, state);
                default -> BankingController.action(audience.player(), audience.menu(), page, "");
            }
        });
    }
    private static void open(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var currency = EconomyCurrency.fromCode(value.trim().toUpperCase(Locale.ROOT));
        if (!audience.menu().page().equals("manage") || audience.menu().access() != BankingMenu.Access.TELLER
                || !state.openCurrencies.contains(currency)) throw new IllegalArgumentException("Choose an offered currency at a teller");
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), null).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null || context.terminal() == null || context.terminal().kind() != TerminalPolicy.Kind.TELLER) {
                reply(audience, "manage", List.of(), "Open your first account from My accounts, then visit a teller."); return;
            }
            var open = new AccountLifecycle.Open(currency, context.account().revision());
            var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
            start(audience, services, grant, state, ownedIntent(grant, AccountLifecycle.OPEN, operation, attempt, open.request()),
                    fresh -> services.submitOwned(fresh, AccountLifecycle.OPEN, operation, attempt, open.request(),
                            AccountLifecycle.opening(fresh.target(), open), null), "manage");
        });
    }
    private static void history(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, boolean next) {
        if (next && state.historyCursor == null) { reply(audience, "history", audience.menu().lines(), "End of history"); return; }
        if (!next) state.historyCursor = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, contextError) -> {
            if (!audience.live()) return;
            if (contextError != null || context.account() == null) { unavailable(audience, "history"); return; }
            services.ledger().funds(grant, context.account().id()).whenComplete((funds, fundsError) -> {
                if (!audience.live()) return;
                if (fundsError != null || funds.isEmpty()) { unavailable(audience, "history"); return; }
                services.ledger().history(grant, context.account().id(), state.historyCursor, 8).whenComplete((found, error) -> {
                    if (!audience.live()) return;
                    if (error != null || found.isEmpty()) { unavailable(audience, "history"); return; }
                    var page = found.orElseThrow(); state.historyCursor = page.nextCursor();
                    var lines = new ArrayList<String>();
                    lines.add("Transaction History");
                    lines.add("Balance: " + money(new Money(funds.orElseThrow().currency(), funds.orElseThrow().availableMinorUnits())));
                    for (var entry : page.entries()) {
                        lines.add("#" + entry.sequence().value() + " " + money(entry.amount()));
                        lines.add("Transaction " + entry.transactionId().value());
                    }
                    if (page.entries().isEmpty()) lines.add("No transactions yet.");
                    reply(audience, "history", lines, state.historyCursor == null ? "All entries shown" : "More history available", List.of());
                });
            });
        });
    }
    private static void closePreview(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        if (audience.menu().access() != BankingMenu.Access.KIOSK || !audience.menu().page().equals("branch")
                || !BankingController.canChangeAccount(audience.menu())) throw new IllegalArgumentException("Choose an account at its branch kiosk");
        var offered = state.offered.get(UUID.fromString(value));
        if (offered == null || audience.menu().choices().stream().noneMatch(choice -> choice.action().equals("branch_close")
                && choice.value().equals(value))) throw new IllegalArgumentException("Choose an eligible offered account");
        state.selected = offered.id();
        state.closure = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null || context.terminal() == null
                    || context.terminal().kind() != TerminalPolicy.Kind.KIOSK || !context.terminal().active()
                    || !context.terminal().generation().equals(audience.menu().generation())) { unavailable(audience, "manage"); return; }
            var source = context.account();
            if (source.status() != AccountIdentity.Status.OPEN) { unavailable(audience, "manage"); return; }
            if (source.primary()) { reply(audience, "manage", List.of(), failureMessage("ACCOUNT_PROTECTED")); return; }
            services.ledger().primaryFunds(grant).thenCombine(services.ledger().funds(grant, source.id()), (primary, funds) -> {
                if (primary.isEmpty() || funds.isEmpty()) throw new IllegalStateException("Account unavailable");
                return Map.entry(primary.orElseThrow(), funds.orElseThrow());
            }).whenComplete((found, fundsError) -> {
                if (!audience.live()) return;
                if (fundsError != null) { unavailable(audience, "manage"); return; }
                var destination = found.getKey().account(); var funds = found.getValue();
                if (funds.reservedMinorUnits() != 0 || funds.restrictedMinorUnits() != 0) {
                    reply(audience, "manage", List.of(), failureMessage("ACTIVE_HOLD")); return;
                }
                var close = new AccountLifecycle.Close(source.id(), source.revision(), destination.id(), destination.revision());
                state.closureTerminalRevision = context.terminal().revision();
                if (funds.postedMinorUnits() == 0) {
                    showClosure(audience, state, new FundedClosure.Instruction(close, null),
                            List.of("Close your " + source.currency().code() + " account?", "The account is empty.",
                                    "Its transaction history will be preserved.", "Closing it frees one of your four account slots."));
                } else services.ledger().quoteClosure(grant, new RequestId(UUID.randomUUID()),
                        new FxQuotes.Pair(source.currency(), destination.currency()), funds.posted())
                        .whenComplete((quote, quoteError) -> {
                            if (!audience.live()) return;
                            if (quoteError != null) { unavailable(audience, "manage"); return; }
                            long credited = displayCredit(quote, true);
                            showClosure(audience, state, new FundedClosure.Instruction(close, quote.id()),
                                    List.of("Close your " + source.currency().code() + " account?",
                                            "Transfer balance: " + money(funds.posted()),
                                            "To primary: " + money(new Money(destination.currency(), credited)),
                                            "Fee: " + money(quote.fee()), "History is preserved. Confirm before the quote expires."));
                        });
            });
        });
    }
    private static void showClosure(Audience audience, State state, FundedClosure.Instruction closure, List<String> lines) {
        state.closure = closure;
        reply(audience, "close", lines, "Review account closure", List.of(
                new BankingNetwork.Choice("account_close_confirm", "", "Confirm closure", false)));
        state.closureRevision = audience.menu().revision();
    }
    private static void closeConfirm(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (audience.menu().access() != BankingMenu.Access.KIOSK || !audience.menu().page().equals("close")
                || audience.menu().revision() != state.closureRevision || state.closure == null
                || !BankingController.canChangeAccount(audience.menu())) throw new IllegalArgumentException("Review closure first");
        var closure = state.closure;
        var view = state.branchView;
        if(view==null || view.terminal().revision()!=state.closureTerminalRevision)throw new IllegalArgumentException("Stale branch preview");
        var lease=proof(audience,grant,view,BranchAdmission.Action.CLOSE,null);
        var request=new BankProviders.Request(lease,0,closure.request().fields()).request();
        var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        start(audience, services, grant, state, new Journal.Intent(operation,attempt,grant.actor(),BankProviders.CLOSE,request,null,null),
                fresh -> services.ledger().closeAtBranch(fresh,operation,attempt,closure,BankingClosureClaims::read,lease), "close");
        state.closure=null; state.branchView=null;
    }

    private static void fx(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        state.exchange = null; state.quote = null; state.fxTerminal = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.fx() == null || context.account() == null) { unavailable(audience, "fx"); return; }
            state.selected = context.account().id();
            ownerAccounts(services, grant).whenComplete((found, accountError) -> {
                if (!audience.live()) return;
                if (accountError != null || found.isEmpty()) { unavailable(audience, "fx"); return; }
                state.offered = offeredAccounts(found.orElseThrow().accounts());
                var choices = accountChoices(state, "fx");
                int destinations = 0;
                for (var account : state.offered.values()) {
                    if (account.currency() == context.account().currency()) continue;
                    choices.add(new BankingNetwork.Choice("destination", account.id().value().toString(), account.currency().code() + " account", false));
                    destinations++;
                }
                var lines = destinations == 0
                        ? List.of("Open another currency account at its bank kiosk to exchange money.")
                        : List.of("From: " + context.account().currency().code() + " account", "Choose a destination account and enter the amount to exchange.", "Review the amount received and fee before confirming.");
                reply(audience, "fx", lines, destinations == 0 ? "Another currency account is needed" : "Choose accounts and amount", choices);
            });
        });
    }
    static Money exchangeAmount(EconomyCurrency source, EconomyCurrency target, String text) {
        long units;
        if (source == EconomyCurrency.CHF || target == EconomyCurrency.CHF) {
            if (!text.matches("[0-9]{1,19}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Exchange amount");
            units = new BigDecimal(text).movePointRight(source.fractionDigits()).longValueExact();
        } else units = Math.multiplyExact(Long.parseLong(text), source.minorUnitsPerMajor());
        if (units < 1) throw new IllegalArgumentException("Exchange amount");
        return new Money(source, units);
    }

    private static long displayCredit(FxQuotes.Quote quote, boolean drain) {
        boolean minor = quote.pricing()!=null || quote.pair().source() == EconomyCurrency.CHF || quote.pair().target() == EconomyCurrency.CHF
                || drain && quote.debit().minorUnits() % quote.pair().source().minorUnitsPerMajor() != 0;
        return minor ? quote.credit().minorUnits()
                : quote.credit().minorUnits() - quote.credit().minorUnits() % quote.credit().currency().minorUnitsPerMajor();
    }

    private static void exchangePreview(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var fields = value.trim().split("\\s+", -1);
        if (fields.length != 2) throw new IllegalArgumentException("Exchange input");
        var target = offered(state, fields[0]);
        String amountText = fields[1];
        state.exchange = null; state.quote = null; state.fxTerminal = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null) { unavailable(audience, "fx"); return; }
            if(!audience.menu().remote() && (context.terminal()==null || context.terminal().binding()==null || !context.terminal().binding().bound())) {
                reply(audience,"fx",List.of("The terminal must be bound to a bank before a new exchange."),"Bank selection required",List.of());return;
            }
            state.fxTerminal=context.terminal();
            var source = context.account();
            services.findOwnedAccount(grant, target.id()).whenComplete((found, targetError) -> {
                if (!audience.live()) return;
                if (targetError != null || found.isEmpty()) { unavailable(audience, "fx"); return; }
                var destination = found.orElseThrow();
                try {
                    if (source.status() != AccountIdentity.Status.OPEN || destination.status() != AccountIdentity.Status.OPEN
                            || source.currency() == destination.currency()) throw new IllegalArgumentException("Exchange accounts");
                    var amount = exchangeAmount(source.currency(), destination.currency(), amountText);
                    services.ledger().quote(grant, new RequestId(UUID.randomUUID()), new FxQuotes.Pair(source.currency(), destination.currency()),
                            FxQuotes.Side.EXACT_DEBIT, amount).whenComplete((quote, quoteError) -> {
                        if (!audience.live()) return;
                        if (quoteError != null) { unavailable(audience, "fx"); return; }
                        state.quote = quote;
                        state.exchange = new FxSettlement.Exchange(quote.id(), new PlayerId(audience.player().getUUID()),
                                new SameCurrency.Revision(source.id(), source.revision()), new SameCurrency.Revision(destination.id(), destination.revision()));
                        long credited = displayCredit(quote, false);
                        reply(audience, "fx", List.of("Debit " + money(quote.debit()), "Credit " + money(new Money(quote.credit().currency(), credited)),
                                "Fee " + money(quote.fee()), "Quote expires " + quote.expires(), "Choose Confirm exchange before this quote expires."), "Exchange preview");
                    });
                } catch (IllegalArgumentException | ArithmeticException invalid) { reply(audience, "fx", List.of("Choose two open accounts and a positive amount. CHF pairs accept exact cents; other pairs require whole units."), "Invalid exchange"); }
            });
        });
    }
    private static void exchangeConfirm(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (state.exchange == null || state.quote == null || !audience.menu().page().equals("fx")) throw new IllegalArgumentException("Preview required");
        var exchange = state.exchange;
        var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        var intent = new Journal.Intent(operation, attempt, grant.actor(), FxSettlement.EXCHANGE, exchange.request(), null, null);
        var terminal=state.fxTerminal;
        var remoteToken=audience.menu().issuedToken();var remoteActor=new PlayerId(audience.player().getUUID());
        var live=audience.menu().remote()?null:KioskAdmission.financialAuthority(audience.player(),audience.menu(),terminal,intent);
        start(audience, services, grant, state, intent, fresh -> services.ledger().exchange(fresh, operation, attempt, exchange,
                (candidate,reads)->(remoteToken==null || ca.emilybelnavis.mcmods.economy.card.CardAccess.usable(reads,grant.target().world(),remoteActor,remoteToken,System.currentTimeMillis()).isPresent()) && (live==null || live.allows(TerminalPolicy.Action.CONFIGURE,candidate,terminal,reads)
                    && TerminalBanking.matches(reads,grant.target().world(),terminal.anchor(),terminal.generation(),terminal.revision(),terminal.binding().institution()))), "fx");
        state.exchange = null; state.quote = null; state.fxTerminal = null;
    }
    private static void brand(Audience audience,ServerServices services,PartyAuthorization.Grant grant,State state,String action,String value) {
        if(audience.menu().access()!=BankingMenu.Access.ADMIN || !offered(audience,action,value))throw new IllegalArgumentException("Brand selection unavailable");
        if(action.equals("brand_preview") && value.isEmpty()) {
            var choices=InstitutionCatalog.banks().stream().map(bank->new BankingNetwork.Choice("brand_preview",bank.id().key(),bank.name()+" ("+bank.baseCurrency().code()+")",false)).toList();
            reply(audience,"brands",List.of(),"Choose a bank",choices);return;
        }
        audience.menu().setBusy(true);boolean operator=audience.player().hasPermissions(2);
        services.ledger().terminalSettings(grant.actor(),(actor,reads)->operator,audience.menu().anchor()).whenComplete((found,error)->{
            if(!audience.live())return;
            if(error!=null || found.isEmpty()){unavailable(audience,"settings");return;}
            var current=found.orElseThrow();
            if(action.equals("brand_preview")) {
                var bank=InstitutionCatalog.require(new InstitutionCatalog.InstitutionId(value));
                state.pendingBrand=bank.id().key();state.brandRevision=current.revision();
                reply(audience,"brand_review",List.of("Bind this machine to "+bank.name()+"?","Native currency: "+bank.baseCurrency().code(),
                        "Existing settings are retained as legacy metadata.","Cash service will be disabled. Enable native cash settings separately.",
                        "Pending cash/custody work must finish before binding."),"Confirm native-currency change",List.of(new BankingNetwork.Choice("brand_confirm",bank.id().key(),"Confirm bank binding",false)));return;
            }
            if(state.pendingBrand==null || !state.pendingBrand.equals(value) || state.brandRevision!=current.revision()) {
                reply(audience,"settings",List.of("The terminal changed. Review the bank again."),"Stale binding preview",List.of());return;
            }
            var request=TerminalBanking.bindRequest(current.anchor(),current.generation(),current.revision(),new InstitutionCatalog.InstitutionId(state.pendingBrand),true);
            var operation=new OperationId(UUID.randomUUID());var attempt=new RequestId(UUID.randomUUID());
            var intent=new Journal.Intent(operation,attempt,grant.actor(),TerminalBanking.BIND,request,null,null);
            var authority=KioskAdmission.authority(audience.player(),audience.menu(),current,intent);
            start(audience,services,grant,state,intent,fresh->services.ledger().terminalBanking(
                    (actor,kind)->actor.equals(grant.actor()),authority,(actor,reads)->operator).submit(intent),"settings");
            state.pendingBrand=null;
        });
    }

    private static void settings(Audience audience, ServerServices services) {
        audience.menu().setBusy(true);
        var actor = new PlayerId(audience.player().getUUID()); boolean operator = audience.player().hasPermissions(2);
        services.ledger().terminalSettings(actor, (who, reads) -> operator, audience.menu().anchor()).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { reply(audience, "settings", List.of("Only this terminal's owner or an operator may edit its settings."), "Owner access required"); return; }
            var terminal = found.orElseThrow();
            if (!terminal.generation().equals(audience.menu().generation()) || !terminal.active()) {
                reply(audience, "settings", List.of("Reopen this terminal to see its current settings."), "Terminal changed", List.of()); return;
            }
            var binding=terminal.binding();
            boolean bound=binding!=null && binding.bound();
            String identity=bound?InstitutionCatalog.require(binding.institution()).name()+" — "+binding.nativeCurrency().code():"UNBOUND_BANK — bank selection required";
            var lines=new ArrayList<String>();lines.add(identity);
            lines.add("Generation: "+terminal.generation());lines.add("Revision: "+terminal.revision()+"; kind: "+terminal.kind());
            lines.add("Migration: "+(bound?binding.migrationState():"UNBOUND_BANK"));
            lines.add("Cash: "+terminal.settings().mode()+"; native denominations only.");
            var controls=new ArrayList<BankingNetwork.Choice>();
            if(terminal.kind()!=TerminalPolicy.Kind.REGISTER)controls.add(new BankingNetwork.Choice("brand_preview","","Select bank brand",false));
            if(bound && terminal.kind()!=TerminalPolicy.Kind.KIOSK && terminal.kind()!=TerminalPolicy.Kind.REGISTER) {
                String form=terminal.settings().mode().name()+";"+binding.nativeCurrency().code()+";all";
                controls.add(new BankingNetwork.Choice("configure",form,"Save native-currency settings",true));
            }
            var position=new net.minecraft.core.BlockPos(terminal.anchor().x(),terminal.anchor().y(),terminal.anchor().z());
            var physical=audience.player().serverLevel().getBlockState(position);
            if(terminal.kind()==TerminalPolicy.Kind.KIOSK&&physical.getBlock() instanceof ca.emilybelnavis.mcmods.economy.terminal.BranchKioskBlock
                    &&!physical.getValue(ca.emilybelnavis.mcmods.economy.terminal.BranchKioskBlock.WIDE)){
                lines.add("Legacy narrow kiosk. Upgrade is free and needs a clear, supported extra column.");
                controls.add(new BankingNetwork.Choice("kiosk_upgrade","","Upgrade kiosk",false));
            }
            if(audience.player().serverLevel().getBlockEntity(position) instanceof ca.emilybelnavis.mcmods.economy.terminal.TerminalBlockEntity entity)entity.applyPolicy(terminal);
            reply(audience,"settings",lines,"Owner/operator configuration",controls);
        });
    }
    private static void configure(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var actor = new PlayerId(audience.player().getUUID());
        boolean operator = audience.player().hasPermissions(2);
        audience.menu().setBusy(true);
        services.ledger().terminalSettings(actor, (who, reads) -> operator, audience.menu().anchor()).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { reply(audience, "settings", List.of("Only the owner or an operator may edit settings."), "Owner access required"); return; }
            try {
                var current = found.orElseThrow();
                if (!current.generation().equals(audience.menu().generation())) throw new IllegalArgumentException("Placement changed");
                var settings = parseSettings(value, current.kind());
                var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
                var request = TerminalPolicy.configureRequest(current.anchor(), current.generation(), current.revision(), settings);
                var intent = new Journal.Intent(operation, attempt, actor, TerminalPolicy.Action.CONFIGURE.kind(), request, null, null);
                var authority=KioskAdmission.authority(audience.player(),audience.menu(),current,intent);
                start(audience, services, grant, state, intent, fresh -> {
                    boolean live = audience.menu().valid(audience.player());
                    boolean currentOperator = audience.player().hasPermissions(2);
                    return services.ledger().terminalPolicy(TerminalPolicy.Action.CONFIGURE,
                            (who, kind) -> who.equals(actor), authority,
                            (who, reads) -> currentOperator, null).submit(intent);
                }, "settings");
            } catch (IllegalArgumentException invalid) { reply(audience, "settings",
                    List.of("Enter BOTH CAD, DEPOSIT CAD, WITHDRAW CAD, or OFF."), "Check the setting and try again"); }
        });
    }
    static TerminalPolicy.Settings parseSettings(String value, TerminalPolicy.Kind kind) {
        return parseSettings(value, kind, CashCatalog.all());
    }
    static TerminalPolicy.Settings parseSettings(String value, TerminalPolicy.Kind kind, java.util.List<CashCatalog.Denomination> catalog) {
        var simple = value.trim().split("\\s+", -1);
        if (simple.length == 1 && simple[0].equalsIgnoreCase("OFF")) return TerminalPolicy.Settings.disabled();
        if (simple.length == 2 && simple[0].matches("(?i)BOTH|DEPOSIT|WITHDRAW"))
            value = simple[0].toUpperCase(Locale.ROOT) + ";" + simple[1].toUpperCase(Locale.ROOT) + ";all";
        var fields = value.trim().split(";", -1);
        if (fields.length != 3) throw new IllegalArgumentException("Settings fields");
        var mode = TerminalPolicy.Mode.valueOf(fields[0].trim().toUpperCase(Locale.ROOT));
        if (mode == TerminalPolicy.Mode.DISABLED) return TerminalPolicy.Settings.disabled();
        if (kind == TerminalPolicy.Kind.REGISTER) throw new IllegalArgumentException("Register cash mode");
        var currencies = EnumSet.noneOf(EconomyCurrency.class);
        for (var code : fields[1].split(",", -1)) currencies.add(EconomyCurrency.fromCode(code.trim().toUpperCase(Locale.ROOT)));
        var denominations = new TreeSet<String>();
        if (fields[2].trim().equalsIgnoreCase("all")) {
            for (var entry : catalog) if (currencies.contains(entry.currency()) && (kind != TerminalPolicy.Kind.ATM || entry.bill())) denominations.add("economy:" + entry.id());
        } else {
            if (fields[2].split(",", -1).length > TerminalPolicy.MAX_DENOMINATIONS) throw new IllegalArgumentException("Cash type bound");
            for (var id : fields[2].split(",", -1)) {
                var key = id.trim(); if (!key.startsWith("economy:")) key = "economy:" + key;
                final String wanted = key;
                var entry = catalog.stream().filter(item -> ("economy:" + item.id()).equals(wanted)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown cash type"));
                if (!currencies.contains(entry.currency()) || kind == TerminalPolicy.Kind.ATM && !entry.bill()) throw new IllegalArgumentException("Cash type currency");
                denominations.add(key);
            }
        }
        return new TerminalPolicy.Settings(mode, currencies, denominations);
    }
    static void financeSubmit(FinanceMenuSupport.Audience audience, ServerServices services,
            PartyAuthorization.Grant grant, Journal.Kind kind, Journal.CanonicalRequest request,
            java.util.function.BiFunction<OperationId, RequestId, JournalService.Submission> submit) {
        var operation = new OperationId(UUID.randomUUID());
        var attempt = new RequestId(UUID.randomUUID());
        var state = STATES.computeIfAbsent(audience.menu(), ignored -> new State());
        start(new Audience(audience.player(), audience.menu(), audience.session(), audience.revision()), services,
                grant, state, ownedIntent(grant, kind, operation, attempt, request),
                ignored -> submit.apply(operation, attempt), audience.menu().page());
    }

    private static Journal.Intent ownedIntent(PartyAuthorization.Grant grant, Journal.Kind kind, OperationId operation,
                                              RequestId attempt, Journal.CanonicalRequest request) {
        var fields = new HashMap<>(request.fields());
        fields.put("economy:owner", Journal.actorKey(grant.actor()));
        return new Journal.Intent(operation, attempt, grant.actor(), kind,
                new Journal.CanonicalRequest(request.version(), fields), null, null);
    }
    private static void start(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state,
                              Journal.Intent intent, Function<PartyAuthorization.Grant, JournalService.Submission> submit, String page) {
        if (!audience.live()) return;
        if (audience.player().getPersistentData().contains(PENDING)) {
            reply(audience, page, List.of("A previous banking operation needs its original outcome checked. Choose Retry banking."), "Previous operation unresolved"); return;
        }
        var tag = new CompoundTag();
        tag.putUUID("world", grant.target().world().value()); tag.putUUID("actor", audience.player().getUUID());
        tag.putUUID("operation", intent.operationId().value()); tag.putUUID("request", intent.requestId().value());
        tag.putString("service", intent.kind().service()); tag.putString("type", intent.kind().operationType());
        tag.putInt("version", intent.request().version());
        var fields = new CompoundTag(); intent.request().fields().forEach(fields::putString); tag.put("fields", fields);
        if (audience.player().getPersistentData().getCompound("economy_banking_cash_pending").hasUUID("operation"))
            throw new IllegalStateException("Resolve the pending cash operation first.");
        audience.player().getPersistentData().put(PENDING, tag);
        state.retry = submit;
        audience.menu().setBusy(true);
        boolean reachedSubmission = false;
        try {
            BankingSaves.player(audience.player(), grant.target().world(), intent.operationId().value());
            reachedSubmission = true;
            // The same original operation and canonical request are retained if admission or publication is uncertain.
            submit.apply(grant).completion().whenComplete((delivery, error) -> finish(audience, grant.target().world(), state, intent, page,
                    error == null ? delivery.outcome() : null));
        } catch (Exception unavailable) {
            if (!reachedSubmission) {
                audience.player().getPersistentData().remove(PENDING); state.retry = null;
                reply(audience, page, List.of("The request was not submitted because its save could not be verified."), "Banking save unavailable");
            } else reply(audience, page, List.of("Your original operation is retained. Choose Retry banking to check it."), "Banking admission unavailable");
        }
    }
    private static Journal.Intent pending(ServerPlayer player, WorldId world) {
        var tag = player.getPersistentData().getCompound(PENDING);
        if (!tag.hasUUID("world") || !tag.getUUID("world").equals(world.value())
                || !tag.hasUUID("actor") || !tag.getUUID("actor").equals(player.getUUID())) throw new IllegalArgumentException("Pending banking owner");
        var kind = new Journal.Kind(tag.getString("service"), tag.getString("type"));
        if (!Set.of(ca.emilybelnavis.mcmods.economy.account.AccountCatalog.INITIALIZE_PRIMARY, AccountLifecycle.OPEN, AccountLifecycle.CLOSE, BankProviders.PRIMARY, BankProviders.SECONDARY, BankProviders.ASSIGN, BankProviders.CLOSE, BankProviders.SWITCH_SOURCE, BankProviders.SWITCH_TARGET,
                FxSettlement.EXCHANGE, TerminalPolicy.Action.CONFIGURE.kind(), TerminalBanking.BIND,ca.emilybelnavis.mcmods.economy.card.IssuedCards.ISSUE,ca.emilybelnavis.mcmods.economy.card.IssuedCards.REVOKE,
                ca.emilybelnavis.mcmods.economy.credit.CreditFacilities.ISSUE, ca.emilybelnavis.mcmods.economy.credit.CreditFacilities.INCREASE,
                ca.emilybelnavis.mcmods.economy.credit.CreditFacilities.REPAY, ca.emilybelnavis.mcmods.economy.credit.CreditFacilities.DRAW,
                ca.emilybelnavis.mcmods.economy.credit.CreditFacilities.REPLACE, ca.emilybelnavis.mcmods.economy.credit.CreditFacilities.REVOKE, ca.emilybelnavis.mcmods.economy.loan.Loans.APPLY,
                ca.emilybelnavis.mcmods.economy.loan.Loans.REPAY, ca.emilybelnavis.mcmods.economy.debt.DebtPayments.PAY, ca.emilybelnavis.mcmods.economy.autopay.Autopay.CONFIGURE).contains(kind)) throw new IllegalArgumentException("Pending banking kind");
        var fieldsTag = tag.getCompound("fields");
        if (fieldsTag.size() > 64) throw new IllegalArgumentException("Pending banking fields");
        var fields = new HashMap<String, String>();
        for (var key : fieldsTag.getAllKeys()) fields.put(key, fieldsTag.getString(key));
        return new Journal.Intent(new OperationId(tag.getUUID("operation")), new RequestId(tag.getUUID("request")),
                new PlayerId(player.getUUID()), kind, new Journal.CanonicalRequest(tag.getInt("version"), fields), null, null);
    }
    private static void retry(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (!audience.player().getPersistentData().contains(PENDING)) {
            reply(audience, audience.menu().page(), List.of("There is no unresolved banking operation."), "Nothing to retry"); return;
        }
        var intent = pending(audience.player(), grant.target().world());
        audience.menu().setBusy(true);
        var session = services.sessionId(); var world = grant.target().world();
        services.journal((actor, kind) -> grant.allows(world, session, actor, kind), Map.of(), null).lookup(intent).whenComplete((found, error) -> {
            if (error != null) { finish(audience, world, state, intent, audience.menu().page(), null); return; }
            if (found.isPresent()) { finish(audience, world, state, intent, audience.menu().page(), found.orElseThrow()); return; }
            if (!audience.live()) return;
            if (state.retry == null || intent.kind().equals(ca.emilybelnavis.mcmods.economy.autopay.Autopay.CONFIGURE) || intent.kind().equals(ca.emilybelnavis.mcmods.economy.debt.DebtPayments.PAY) || intent.kind().service().equals("economy:credit") || intent.kind().service().equals("economy:loans") || (intent.kind().service().equals("economy:providers") || intent.kind().service().equals("economy:cards")) || Set.of(AccountCatalog.INITIALIZE_PRIMARY, AccountLifecycle.OPEN, AccountLifecycle.CLOSE).contains(intent.kind())) {
                audience.player().getPersistentData().put("economy_banking_last_absent", audience.player().getPersistentData().getCompound(PENDING).copy());
                audience.player().getPersistentData().remove(PENDING);
                reply(audience, audience.menu().page(), List.of("Operation " + intent.operationId().value(),
                        "The ordered original-outcome lookup confirms no committed result. No replacement operation was submitted.",
                        "You may prepare a fresh request."), "Original operation was not committed");
                return;
            }
            try {
                BankingSaves.player(audience.player(), world, intent.operationId().value());
                state.retry.apply(grant).completion().whenComplete((delivery, submitError) -> finish(audience, world, state, intent,
                        audience.menu().page(), submitError == null ? delivery.outcome() : null));
            } catch (Exception unavailable) { finish(audience, world, state, intent, audience.menu().page(), null); }
        });
    }
    private static void finish(Audience audience, WorldId world, State state, Journal.Intent intent, String page, Journal.Outcome outcome) {
        boolean terminal = outcome != null && (outcome.status() == Journal.Status.COMMITTED || outcome.status() == Journal.Status.DURABLY_REJECTED);
        if (terminal) {
            var saved = audience.player().getPersistentData().getCompound(PENDING);
            if (saved.hasUUID("operation") && saved.getUUID("operation").equals(intent.operationId().value())) {
                audience.player().getPersistentData().remove(PENDING);
                // A disconnected old ServerPlayer must never overwrite a newer login's inventory file.
                if (audience.player().server.getPlayerList().getPlayer(audience.player().getUUID()) == audience.player()) {
                    try { BankingSaves.player(audience.player(), world, intent.operationId().value()); }
                    catch (Exception ignored) { /* A persisted old marker is safe: exact lookup will resolve it again. */ }
                }
            }
            state.retry = null;
        }
        if (!audience.live()) return;
        var lines = new ArrayList<String>();
        if (outcome != null && outcome.status() == Journal.Status.COMMITTED) lines.add(switch (page) {
            case "setup" -> "Your primary account is ready.";
            case "manage" -> "Your account is ready.";
            case "accounts" -> "Your account is ready.";
            case "close" -> "Your account was closed. Its history is preserved.";
            case "fx" -> "Your exchange is complete.";
            case "settings" -> "The terminal settings were saved.";
            case "cards" -> intent.kind().equals(ca.emilybelnavis.mcmods.economy.card.IssuedCards.ISSUE)?"Your card issuance committed. Use Collect items to receive it.":"The token is revoked; every copy is invalid.";
            default -> "Done.";
        });
        else if (outcome != null && outcome.status() == Journal.Status.DURABLY_REJECTED)
            lines.add(failureMessage(outcome.result().code()));
        lines.add("Operation " + intent.operationId().value());
        if (outcome != null) lines.add(outcome.result().code().replace('_', ' ').toLowerCase(Locale.ROOT));
        if (!terminal) lines.add("Keep the original operation. Choose Retry banking before another mutation.");
        String resultStatus = !terminal ? "Banking outcome needs checking"
                : outcome.status() == Journal.Status.DURABLY_REJECTED ? failureMessage(outcome.result().code())
                : (intent.kind().equals(AccountLifecycle.CLOSE) || intent.kind().equals(BankProviders.CLOSE)) ? "Account closed. An account slot is now available." : "Banking operation resolved";
        if (page.equals("close")) reply(audience, page, lines, resultStatus, List.of());
        else reply(audience, page, lines, resultStatus);
        if (outcome != null && outcome.status() == Journal.Status.COMMITTED
                && (intent.kind().equals(TerminalPolicy.Action.CONFIGURE.kind()) || intent.kind().equals(TerminalBanking.BIND)))
            action(audience.player(), audience.menu(), "settings", "");
        if (outcome != null && outcome.status() == Journal.Status.COMMITTED) {
            if(intent.kind().service().equals("economy:cards")) {cards(new Audience(audience.player(),audience.menu(),audience.menu().session(),audience.menu().revision()),Economy.serverServices(audience.player().server),grant(audience.player(),Economy.serverServices(audience.player().server)),state,state.cardAccount==null?"":state.cardAccount.value().toString());return;}
            if ((intent.kind().equals(AccountLifecycle.CLOSE) || intent.kind().equals(BankProviders.CLOSE))) state.selected = null;
            if (intent.kind().service().equals("economy:providers") || intent.kind().equals(AccountLifecycle.OPEN) || (intent.kind().equals(AccountLifecycle.CLOSE) || intent.kind().equals(BankProviders.CLOSE)))
                action(audience.player(), audience.menu(), "manage_accounts", "");
            else if (intent.kind().equals(AccountCatalog.INITIALIZE_PRIMARY))
                action(audience.player(), audience.menu(), "accounts", "");
        }
    }
    static String failureMessage(String code) {
        return switch (code) {
            case "PRICING_QUOTE_STALE", "FX_PRICING_REQUIRED", "FX_PROVIDER_STALE" -> "Account service or pricing changed. Review a fresh quote.";
            case "YIELD_SETTLEMENT_REQUIRED", "YIELD_FUNDING_UNAVAILABLE" -> "Earned deposit yield must be settled before closure. Existing funds remain available.";
            case "BRANCH_REQUIRED" -> "Visit the account institution's branded branch kiosk.";
            case "BANK_CAP_REACHED" -> "Limit: 4 open accounts. A bank branch is required for closure.";
            case "BANK_CURRENCY_EXISTS" -> "You already have an open account in this currency.";
            case "ACCOUNT_PROTECTED" -> "Your primary and world-currency accounts cannot be closed.";
            case "ACTIVE_HOLD", "RESTRICTED_FUNDS" -> "This account has reserved or restricted funds. Resolve them before closing.";
            case "UNRESOLVED_OBLIGATION" -> "Resolve pending payments, refunds or item deliveries before closing.";
            case "FX_QUOTE_STALE", "FX_QUOTE_CONSUMED", "FX_CLOSURE_AMOUNT", "ACCOUNT_STALE", "DESTINATION_STALE" -> "The account or quote changed. Review the closure or exchange again.";
            case "PRIMARY_REQUIRED" -> "Open your first account from My accounts.";
            default -> "Request declined. Refresh your accounts and review your choices.";
        };
    }

    private static void reply(Audience audience, String page, List<String> lines, String status) {
        if (!audience.live()) return;
        audience.menu().setBusy(false);
        audience.menu().publish(page, lines, status);
    }
    private static void reply(Audience audience, String page, List<String> lines, String status, List<BankingNetwork.Choice> choices) {
        if (!audience.live()) return;
        audience.menu().setBusy(false);
        audience.menu().publish(page, lines, status, choices);
    }
    private static void unavailable(Audience audience, String page) {
        reply(audience, page, List.of("The account or terminal is unavailable. Refresh My accounts and try again."), "Banking view unavailable", List.of());
    }
    private static String money(Money value) { return value.majorUnits().toPlainString() + " " + value.currency().code(); }
    private static String friendlyMode(TerminalPolicy.Mode mode) {
        return switch (mode) {
            case BOTH -> "deposits and withdrawals";
            case DEPOSIT -> "deposits only";
            case WITHDRAW -> "withdrawals only";
            case DISABLED -> "off";
        };
    }
}
