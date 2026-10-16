package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.UserRepository;
import com.moviebooking.moviebookingsystem.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceRegistrationTests {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void validationRejectsInvalidRegistrationFieldsAndMinors() {
        User invalid = validUser();
        invalid.setName("123");
        assertNotNull(userService.validateCustomerRegistration(invalid, null));

        invalid = validUser();
        invalid.setEmail("not-an-email");
        assertNotNull(userService.validateCustomerRegistration(invalid, null));

        invalid = validUser();
        invalid.setTelephone("12345");
        assertNotNull(userService.validateCustomerRegistration(invalid, null));

        invalid = validUser();
        invalid.setDateOfBirth(LocalDate.now().minusYears(17).toString());
        assertNotNull(userService.validateCustomerRegistration(invalid, null));

        invalid = validUser();
        invalid.setUsername("x");
        assertNotNull(userService.validateCustomerRegistration(invalid, null));

        invalid = validUser();
        invalid.setPassword("short");
        assertNotNull(userService.validateCustomerRegistration(invalid, null));

        assertNull(userService.validateCustomerRegistration(validUser(), null));
    }

    @Test
    void reRegistrationUpdatesExistingCustomerWithoutChangingIdentityOrStatus() {
        User existing = validUser();
        existing.setId(27L);
        existing.setRole("CUSTOMER");
        existing.setStatus("ACTIVE");
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(existing));
        when(userRepository.findByUsernameIgnoreCase("newcustomer"))
                .thenReturn(Optional.empty());
        when(userRepository.findById(27L)).thenReturn(Optional.of(existing));

        User registration = validUser();
        registration.setEmail("customer@example.com");
        registration.setUsername("newcustomer");

        assertTrue(userService.registerCustomer(registration, 27L));
        assertEquals(27L, existing.getId());
        assertEquals("CUSTOMER", existing.getRole());
        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("newcustomer", existing.getUsername());
        verify(userRepository).save(existing);
    }

    @Test
    void registrationCannotReuseStaffEmail() {
        User staff = validUser();
        staff.setRole("ADMIN");
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(staff));

        String error = userService.validateCustomerRegistration(validUser(), null);

        assertTrue(error != null);
        assertFalse(userService.registerCustomer(validUser(), null));
    }

    @Test
    void loginRejectsMissingMalformedAndIncorrectCredentials() {
        assertNull(userService.loginUser("", "Moviebook123"));
        assertNull(userService.loginUser("not-an-email", "Moviebook123"));
        assertNull(userService.loginUser("customer@example.com", ""));

        User existing = validUser();
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(existing));
        assertNull(userService.loginUser("customer@example.com", "wrongpass1"));
        assertEquals(existing, userService.loginUser(
                "customer@example.com", "Moviebook123"));
    }

    @Test
    void customerProfileUpdatesOnlyPermittedValidatedFields() {
        User existing = validUser();
        existing.setId(27L);
        existing.setRole("CUSTOMER");
        existing.setStatus("ACTIVE");
        when(userRepository.findById(27L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.of(existing));

        User update = new User();
        update.setName("Updated Customer");
        update.setEmail("customer@example.com");
        update.setTelephone("0777654321");
        update.setDateOfBirth(LocalDate.now().minusYears(21).toString());
        update.setRole("ADMIN");
        update.setStatus("BLOCKED");
        update.setPassword("attackerControlled");

        assertNull(userService.updateCustomerProfile(27L, update));
        assertEquals("Updated Customer", existing.getName());
        assertEquals("0777654321", existing.getTelephone());
        assertEquals("CUSTOMER", existing.getRole());
        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("Moviebook123", existing.getPassword());
        verify(userRepository).save(existing);
    }

    @Test
    void customerProfileRejectsInvalidNameAndDuplicateEmail() {
        User existing = validUser();
        existing.setId(27L);
        existing.setRole("CUSTOMER");
        when(userRepository.findById(27L)).thenReturn(Optional.of(existing));

        User update = new User();
        update.setName("");
        update.setEmail("customer@example.com");
        update.setTelephone("0777654321");
        update.setDateOfBirth(LocalDate.now().minusYears(21).toString());
        assertNotNull(userService.updateCustomerProfile(27L, update));

        update.setName("Updated Customer");
        update.setEmail("taken@example.com");
        User other = validUser();
        other.setId(28L);
        other.setEmail("taken@example.com");
        when(userRepository.findByEmailIgnoreCase("taken@example.com"))
                .thenReturn(Optional.of(other));
        assertNotNull(userService.updateCustomerProfile(27L, update));
    }

    private User validUser() {
        User user = new User();
        user.setName("Jamie Movie");
        user.setEmail("customer@example.com");
        user.setTelephone("0771234567");
        user.setDateOfBirth(LocalDate.now().minusYears(20).toString());
        user.setUsername("jamie_movie");
        user.setPassword("Moviebook123");
        user.setRole("CUSTOMER");
        return user;
    }
}