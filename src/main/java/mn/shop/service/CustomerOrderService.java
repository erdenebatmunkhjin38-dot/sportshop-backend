package mn.shop.service;

import mn.shop.model.CustomerOrder;
import mn.shop.repository.CustomerOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerOrderService {

    @Autowired
    private CustomerOrderRepository orderRepository;

    public List<CustomerOrder> findAll() {
        return orderRepository.findAll();
    }

    public Optional<CustomerOrder> findById(Long id) {
        return orderRepository.findById(id);
    }

    public CustomerOrder save(CustomerOrder order) {
        return orderRepository.save(order);
    }

    public void deleteById(Long id) {
        orderRepository.deleteById(id);
    }
}