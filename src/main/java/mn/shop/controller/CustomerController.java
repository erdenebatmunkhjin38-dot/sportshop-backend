package mn.shop.controller;

import mn.shop.model.Customer;
import mn.shop.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // Энийг заавал байх ёстойг анхаараарай
@RequestMapping("/api/customers")
public class CustomerController {

    @Autowired // Энэ дутуу байсан тул NullPointerException өгөх аюултай байсан
    private CustomerService customerService;

    // GET: http://localhost:8080/api/customers
    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.findAll();
    }

    // GET: http://localhost:8080/api/customers/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return customerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST: http://localhost:8080/api/customers
    @PostMapping
    public Customer createCustomer(@RequestBody Customer customer) {
        return customerService.save(customer);
    }

    // PUT: http://localhost:8080/api/customers/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @RequestBody Customer customerDetails) {
        return customerService.findById(id).map(customer -> {
            customer.setName(customerDetails.getName());
            customer.setEmail(customerDetails.getEmail());
            customer.setPhone(customerDetails.getPhone());
            customer.setAddress(customerDetails.getAddress());
            customer.setCityRegion(customerDetails.getCityRegion());
            if (customerDetails.getCcNumber() != null) customer.setCcNumber(customerDetails.getCcNumber());
            return ResponseEntity.ok(customerService.save(customer));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: http://localhost:8080/api/customers/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}