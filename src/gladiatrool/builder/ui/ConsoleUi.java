package gladiatrool.builder.ui;

import java.io.Console;
import java.util.List;

/** Adaptateur minimal pour que les services ne dependent pas de la console. */
public final class ConsoleUi {
    private final Console console;
    public ConsoleUi() { this.console=System.console(); }
    public String ask(String prompt) { if(console==null) throw new IllegalStateException("Console interactive indisponible."); return console.readLine("%s ",prompt); }
    public int choose(String prompt,List<String> choices) { for(int i=0;i<choices.size();i++) System.out.println((i+1)+". "+choices.get(i)); int selected=Integer.parseInt(ask(prompt)); if(selected<1||selected>choices.size())throw new IllegalArgumentException("Choix invalide.");return selected-1; }
}
