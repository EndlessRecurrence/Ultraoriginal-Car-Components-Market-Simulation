import jade.core.Agent;
public class SupplierAgent extends Agent {
    protected void setup() {
        System.out.println("Supplier agent " + getLocalName() + " has started...");
    }
}
