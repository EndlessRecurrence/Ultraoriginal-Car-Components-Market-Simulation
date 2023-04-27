import jade.core.AID;

import java.io.Serializable;

public class ComponentDeliveryUnit implements Serializable {
    private CarComponent component;
    private AID source;
    private AID destination;

    public ComponentDeliveryUnit(CarComponent component, AID source, AID destination) {
        this.source = source;
        this.destination = destination;
        this.component = component;
    }

    public CarComponent getComponent() {
        return component;
    }

    public void setComponent(CarComponent component) {
        this.component = component;
    }

    public AID getSource() {
        return source;
    }

    public void setSource(AID source) {
        this.source = source;
    }

    public AID getDestination() {
        return destination;
    }

    public void setDestination(AID destination) {
        this.destination = destination;
    }
}
