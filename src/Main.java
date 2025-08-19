import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        // إنشاء المنتجات
        Product cheese = new ProductWithExpiryAndShipping(
                "Cheese", 10.0, 5,
                LocalDate.now().plusDays(5), 1.5);
        Product biscuit = new ProductWithExpiry(
                "Biscuit", 5.0, 10,
                LocalDate.now().plusDays(30));
        Product tv = new ProductWithShipping(
                "TV", 300.0, 2,
                10.0);
        Product mobileScratchCard = new BaseProduct(
                "Mobile Scratch Card", 20.0, 20);

        // إنشاء الزبون بالرصيد
        Customer customer = new Customer("John Doe", 1000.0);

        // إضافة منتجات للسلة (العربة)

        try {
            customer.getCart().addProduct(cheese, 3);
            customer.getCart().addProduct(biscuit, 5);
            customer.getCart().addProduct(tv, 1);
            customer.getCart().addProduct(mobileScratchCard, 5);
        } catch (IllegalArgumentException e) {
            System.err.println("Add to cart error: " + e.getMessage());
        }



        // تنفيذ الشراء
        Order order = new Order(customer, new SimpleShippingService());
        order.checkout();
    }
}





class Order {
    private final Customer customer;
    private final ShippingService shippingService;
    private static final double SHIPPING_FEE_PER_KG = 10.0;
    //static → مشترك بين كل الـ objects  in this class ( مش هنحتاج نعمل نسخه جديده من المتغير دا ).

    public Order(Customer customer, ShippingService shippingService) {
        this.customer = customer;
        this.shippingService = shippingService;
    }


    public void checkout() {
        Cart cart = customer.getCart();
        if(cart.isEmpty()) {
            System.err.println("Error: Cart is empty.");
            return;
        }

        double subtotal = 0.0;
        List<Shippable> toShip = new ArrayList<>();
        for(CartItem item: cart.getItems()) {
            Product p = item.getProduct();
            int qty = item.getQuantity();

            if(qty > p.getQuantity()) {
                System.err.println("Error: Product " + p.getName() + " out of stock or insufficient quantity.");
                return;
            }
            //check if ExpirableProduct ?  انتهي الصلاحيه
            if(p instanceof ExpirableProduct && ((ExpirableProduct)p).isExpired()) {
                System.err.println("Error: Product " + p.getName() + " is expired.");
                return;
            }

            subtotal += p.getPrice()*qty;

            //instanceof is the fun opretor  if  p   is inside shippable ? or not
            if(p instanceof Shippable) {
                for(int i=0; i<qty; i++) {
                    toShip.add((Shippable) p);
                }
            }
        }

        double shippingFees = toShip.stream().mapToDouble(Shippable::getWeight).sum() * SHIPPING_FEE_PER_KG;
        double paidAmount = subtotal + shippingFees;

        if(customer.getBalance() < paidAmount) {
            System.err.println("Error: Insufficient balance.");
            return;
        }

        customer.debitBalance(paidAmount);
        for(CartItem item: cart.getItems()) {
            if(item.getProduct() instanceof BaseProduct) {
                ((BaseProduct)item.getProduct()).reduceQuantity(item.getQuantity());
            }
        }

        if(!toShip.isEmpty()) {
            shippingService.shipItems(toShip);
        }

        System.out.println("Checkout successful!");
        System.out.println("Order subtotal: $" + String.format("%.2f", subtotal));
        System.out.println("Shipping fees: $" + String.format("%.2f", shippingFees));
        System.out.println("Paid amount: $" + String.format("%.2f", paidAmount));
        System.out.println("Customer new balance: $" + String.format("%.2f", customer.getBalance()));
        cart.clear();

    }
}






interface Product {
    String getName();
    double getPrice();
    int getQuantity();
    boolean isAvailable();
}


class BaseProduct implements Product {
    private final String name;
    private final double price;
    private int quantity;


    public BaseProduct(String name, double price, int quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }


    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public boolean isAvailable() { return quantity > 0; }

    public void reduceQuantity(int amount) {
        if (amount > quantity) throw new IllegalArgumentException("Insufficient stock.");
        quantity -= amount;
    }

}



interface ExpirableProduct extends Product {
    LocalDate getExpiryDate();
    boolean isExpired();
}

class ProductWithExpiry extends BaseProduct implements ExpirableProduct {
    private final LocalDate expiryDate;
    public ProductWithExpiry(String name, double price, int quantity, LocalDate expiryDate) {
        super(name, price, quantity);
        this.expiryDate = expiryDate;
    }

    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isExpired() { return LocalDate.now().isAfter(expiryDate); }
    public boolean isAvailable() { return super.isAvailable() && !isExpired(); }
}




interface Shippable {
    double getWeight();
}



class ProductWithShipping extends BaseProduct implements Shippable {
    private final double weight;
    public ProductWithShipping(String name, double price, int quantity, double weight) {
        super(name, price, quantity);
        this.weight = weight;
    }
    public double getWeight() { return weight; }
}



class ProductWithExpiryAndShipping extends ProductWithExpiry implements Shippable {
    private final double weight;
    public ProductWithExpiryAndShipping(String name, double price, int quantity, LocalDate expiryDate, double weight) {
        super(name, price, quantity, expiryDate);
        this.weight = weight;
    }

    public double getWeight() { return weight; }
}



class CartItem {
    private final Product product;
    private int quantity;
    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getTotalPrice() {
        return product.getPrice() * quantity;
    }
}




class Cart {
    private final Map<String,CartItem> items = new HashMap<>();
    public void addProduct(Product product, int quantity) {
        if(quantity <=0)
            throw new IllegalArgumentException("Quantity must be positive.");
        if(!product.isAvailable())
            throw new IllegalArgumentException("Product " + product.getName() + " is not available.");
        if(quantity > product.getQuantity())
            throw new IllegalArgumentException("Not enough stock for product " + product.getName());
        if(items.containsKey(product.getName())) {
            CartItem existing = items.get(product.getName());
            int newQty = existing.getQuantity() + quantity;
            if(newQty > product.getQuantity())
                throw new IllegalArgumentException("Total quantity exceeds stock for " + product.getName());
            existing.setQuantity(newQty);
        } else {
            items.put(product.getName(), new CartItem(product, quantity));
        }
    }
    public Collection<CartItem> getItems() { return items.values(); }
    public boolean isEmpty() { return items.isEmpty(); }
    public void clear() { items.clear(); }
}




interface ShippingService {
    void shipItems(List<Shippable> items);
}




class SimpleShippingService implements ShippingService {
    public void shipItems(List<Shippable> items) {
        System.out.println("Shipping Items:");
        for(Shippable s: items) {
            System.out.println(" - " + ((Product) s).getName() + ", weight: " + s.getWeight() + " kg");
        }
    }
}












class Customer {
    private final String name;
    private double balance;
    private final Cart cart = new Cart();

    public Customer(String name, double balance) { this.name = name; this.balance = balance; }

    public String getName() { return name; }
    public double getBalance() { return balance; }
    public Cart getCart() { return cart; }

    public void debitBalance(double amount) {
        if(amount > balance) throw new IllegalArgumentException("Insufficient balance.");
        balance -= amount;
    }

    //IllegalArgumentException = بنستخدمه لو المستخدم أو الكود بعت قيمة "غير منطقية" للميثود.
    //لو العملية صح، الرصيد يتخصم. لو العملية غلط، الكود يوقف ويقول السبب.

}
