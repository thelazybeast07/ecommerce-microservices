package com.ecommerce.user.mapper;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public Address toEntity(AddressRequest request, Customer customer) {
        Address address = new Address(customer);
        applyRequest(request, address);
        return address;
    }

    /** Copies every updatable field; used for both create and PUT. */
    public void applyRequest(AddressRequest request, Address address) {
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());
        address.setAddressType(request.addressType());
    }

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                // Reading the id of a lazy proxy does not trigger a database query in Hibernate
                address.getCustomer().getId(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.getAddressType());
    }
}
