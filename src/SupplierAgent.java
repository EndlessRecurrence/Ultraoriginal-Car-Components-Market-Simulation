import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SupplierAgent extends Agent {
    private AID[] brokerAgents;
    private Map<CarComponentType, Integer> stock;
    public static Map<CarComponentType, Double> referenceComponentPrices;
    private Map<CarComponentType, Double> prices;

    protected void setup() {
        System.out.println("Supplier agent " + getLocalName() + " has started...");
        referenceComponentPrices = initializeReferenceComponentPrices();
        this.prices = initializeComponentPrices();
        this.stock = initializeStock();
        SequentialBehaviour initializationBehaviour = new SequentialBehaviour();
        initializationBehaviour.addSubBehaviour(createBrokerSearchBehaviour());
        initializationBehaviour.addSubBehaviour(createBrokerRegistrationBehaviour());
        initializationBehaviour.addSubBehaviour(createPriceUpdateBehaviour());
        addBehaviour(initializationBehaviour);
    }

    public static Map<CarComponentType, Double> initializeReferenceComponentPrices() {
        Map<CarComponentType, Double> prices = new HashMap<>();
        prices.put(CarComponentType.CAMSHAFT, 60.0);
        prices.put(CarComponentType.CLUTCH_PLATE, 80.0);
        prices.put(CarComponentType.ALTERNATOR, 250.0);
        return prices;
    }

    private Map<CarComponentType, Double> initializeComponentPrices() {
        Map<CarComponentType, Double> prices = new HashMap<>();
        prices.put(CarComponentType.CAMSHAFT, 60.0);
        prices.put(CarComponentType.CLUTCH_PLATE, 80.0);
        prices.put(CarComponentType.ALTERNATOR, 250.0);
        return prices;
    }

    private Map<CarComponentType, Integer> initializeStock() {
        Map<CarComponentType, Integer> stock = new HashMap<>();
        stock.put(CarComponentType.CAMSHAFT, 100);
        stock.put(CarComponentType.CLUTCH_PLATE, 100);
        stock.put(CarComponentType.ALTERNATOR, 100);
        return stock;
    }

    private Behaviour createBrokerSearchBehaviour() {
        return new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription serviceDescription = new ServiceDescription();
                serviceDescription.setType("car-components-trading-brokership");
                template.addServices(serviceDescription);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    brokerAgents = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        brokerAgents[i] = result[i].getName();
                    }
                    System.out.println("Agents detected by " + getLocalName() + ":");
                    Stream.of(brokerAgents).forEach(System.out::println);
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        };
    }

    private Behaviour createBrokerRegistrationBehaviour() {
        return new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                ACLMessage registrationMessage = new ACLMessage(ACLMessage.SUBSCRIBE);
                registrationMessage.addReceiver(brokerAgents[0]);
                registrationMessage.setContent("supplier_registration");
                send(registrationMessage);
                System.out.println("Supplier " + getLocalName() + " has sent registration to the broker.");
            }
        };
    }

    /**
     *  Generates a random price for a car component based on the hardcoded reference prices.
     *  The price update is usually an update that is of a variation percentage between 0 and 20%.
     *  If the generated price falls below or above the chosen thresholds (i.e. if the update exceeds
     *  or falls under 50% of the reference price, the updated price is set to the corresponding threshold).
     *
     *  Let's assume that the reference price for an engine block is 500.0$.
     *  For example, if an engine block currently costs 500.0$, the random number for the variation
     *  percentage (which is between 0 and 1) is 0.5 and the variation sign is -1.0 (in other words,
     *  negative), then the final result of the updated price will be:
     *       500.0 + (-1.0) * 0.5 * 0.2 * 500.0 = 450.0$
     *   which represents a variation of -10% on the previous price.
     *
     *   In another example, we have another engine block that currently costs 300.0$, the random number for the
     *   variation percentage is 1.0, which multiplied with 0.2 returns 0.2 representing a 20% variation for the price.
     *   The variation sign is -1.0, so the result is:
     *      300.0 + (-1.0) * 1.0 * 0.2 * 300.0 = 240.0
     *   But 240.0 is less than the lower threshold for the price, which is half of the reference price of 500.0,
     *   i.e. 250.0. So, the new price will actually be set to 250.0$
     * **/
    public static Double generateUpdatedPrice(CarComponentType type, Double price) {
        Double sign = (Math.random() < 0.5 ? -1.0 : 1.0);
        Double percentage = Math.random() * 0.2;
        Double newPrice = price + sign * percentage * price;
        Double lowerThreshold = referenceComponentPrices.get(type) - 0.5 * referenceComponentPrices.get(type);
        Double upperThreshold = referenceComponentPrices.get(type) + 0.5 * referenceComponentPrices.get(type);

        if (newPrice < lowerThreshold) {
            newPrice = lowerThreshold;
        } else if (newPrice > upperThreshold) {
            newPrice = upperThreshold;
        }

        return newPrice;
    }

    private Behaviour createPriceUpdateBehaviour() {
        return new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                new CarComponent(CarComponentType.ALTERNATOR, 500.0);
                referenceComponentPrices.entrySet().stream()
                        .map(typePricePair -> new CarComponent(typePricePair.getKey(), generateUpdatedPrice(typePricePair.getKey(), typePricePair.getValue())))
                        .forEach(carComponent -> prices.put(carComponent.type(), carComponent.price()));
                System.out.println("==============================================================");
                System.out.println(getLocalName() + "'s randomly updated prices:");
                Stream.of(prices).forEach(System.out::println);
                System.out.println("==============================================================");
            }
        };
    }

    protected void takeDown() {
        System.out.println("Supplier " + getAID().getName() + " has terminated.");
    }
}
