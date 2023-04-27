import jade.core.AID;

import java.io.Serializable;

public class PriceInformation implements Serializable {
    private AID supplier;
    private Double price;
    private CarComponentType type;


    public PriceInformation(AID supplier, Double price, CarComponentType type) {
        this.supplier = supplier;
        this.price = price;
        this.type = type;
    }

    public AID getSupplier() {
        return supplier;
    }

    public void setSupplier(AID supplier) {
        this.supplier = supplier;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public CarComponentType getType() {
        return type;
    }

    public void setType(CarComponentType type) {
        this.type = type;
    }

}
