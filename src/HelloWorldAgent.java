import jade.core.Agent;
public class HelloWorldAgent extends Agent {
    @Override
    protected void setup() {
        System.out.println("Agent " + getLocalName() + " has started...");
    }
}
