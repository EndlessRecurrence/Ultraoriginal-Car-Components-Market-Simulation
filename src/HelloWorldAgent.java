import jade.core.Agent;
public class HelloWorldAgent extends Agent {
    protected void setup() {
        System.out.println("Agent " + getLocalName() + " has started...");
    }
}
