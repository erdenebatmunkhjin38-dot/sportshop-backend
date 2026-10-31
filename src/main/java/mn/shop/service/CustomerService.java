package mn.shop.service;

import mn.shop.model.Customer;
import mn.shop.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    // Бүх хэрэглэгчдийг авах
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    // ID-аар нь нэг хэрэглэгч авах
    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    // Шинэ хэрэглэгч хадгалах эсвэл засах
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    // Хэрэглэгч устгах
    public void deleteById(Long id) {
        customerRepository.deleteById(id);
    }
}