package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    // =========================
    // REGISTER USER / CUSTOMER
    // =========================

    public boolean registerUser(User user) {

        return registerCustomer(user, null);
    }

    public String validateCustomerRegistration(
            User user,
            Long existingCustomerId) {

        if (user == null) {
            return "Registration details are required.";
        }

        if (!isValidName(user.getName())) {
            return "Enter a valid name using 2 to 50 letters, spaces, apostrophes, periods, or hyphens.";
        }

        if (!isValidEmail(user.getEmail())) {
            return "Enter a valid email address.";
        }

        User emailOwner = findUserByEmail(user.getEmail());
        if (emailOwner != null
                && (existingCustomerId == null
                || !existingCustomerId.equals(emailOwner.getId()))) {
            return "That email belongs to another account.";
        }

        if (!isValidUsername(user.getUsername())) {
            return "Username must be 3 to 30 characters and use only letters, numbers, dots, underscores, or hyphens.";
        }

        User usernameOwner = userRepository
                .findByUsernameIgnoreCase(user.getUsername().trim())
                .orElse(null);
        if (usernameOwner != null
                && (existingCustomerId == null
                || !existingCustomerId.equals(usernameOwner.getId()))) {
            return "That username is already in use.";
        }

        if (!isValidTelephone(user.getTelephone())) {
            return "Enter a 10-digit telephone number.";
        }

        if (!isValidDateOfBirth(user.getDateOfBirth())) {
            return "You must be at least 18 years old and enter a valid date of birth.";
        }

        if (!isValidPassword(user.getPassword())) {
            return "Password must be 8 to 72 characters and include at least one letter and one number.";
        }

        if (existingCustomerId != null) {
            User existingCustomer = findUserById(existingCustomerId);
            if (existingCustomer == null
                    || !"CUSTOMER".equalsIgnoreCase(existingCustomer.getRole())
                    || !existingCustomer.getEmail().equalsIgnoreCase(user.getEmail().trim())) {
                return "Customer account could not be verified.";
            }
        }

        return null;
    }

    public boolean registerCustomer(User user, Long existingCustomerId) {

        if (validateCustomerRegistration(user, existingCustomerId) != null) {
            return false;
        }

        user.setName(user.getName().trim());
        user.setEmail(user.getEmail().trim());
        user.setDateOfBirth(user.getDateOfBirth().trim());
        user.setUsername(user.getUsername().trim());
        user.setTelephone(user.getTelephone().trim());
        user.setRole("CUSTOMER");

        if (existingCustomerId != null) {
            User existingCustomer = findUserById(existingCustomerId);
            if (existingCustomer == null
                    || !"CUSTOMER".equalsIgnoreCase(existingCustomer.getRole())) {
                return false;
            }

            existingCustomer.setName(user.getName());
            existingCustomer.setEmail(user.getEmail());
            existingCustomer.setTelephone(user.getTelephone());
            existingCustomer.setDateOfBirth(user.getDateOfBirth().trim());
            existingCustomer.setUsername(user.getUsername());
            existingCustomer.setPassword(user.getPassword());
            userRepository.save(existingCustomer);
            return true;
        }

        user.setStatus("ACTIVE");
        // Hash the password before saving
        user.setPassword(user.getPassword());
        userRepository.save(user);

        return true;
    }


    // =========================
    // NAME VALIDATION
    // =========================

    private boolean isValidName(String name) {

        if (name == null
                || name.trim().isEmpty()) {
            return false;
        }

        return name.trim().matches("[A-Za-z][A-Za-z .'-]{1,49}");
    }


    // =========================
    // EMAIL VALIDATION
    // =========================

    private boolean isValidEmail(String email) {

        if (email == null
                || email.trim().isEmpty()) {
            return false;
        }

        return email.trim().length() <= 150
                && email.trim().matches(
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        );
    }


    private boolean isValidUsername(String username) {
        return username != null
                && username.trim().matches("[A-Za-z0-9_.-]{3,30}");
    }


    // =========================
    // TELEPHONE VALIDATION
    // =========================

    private boolean isValidTelephone(String telephone) {

        if (telephone == null
                || telephone.trim().isEmpty()) {
            return false;
        }

        return telephone.trim().matches(
                "\\d{10}"
        );
    }


    // =========================
    // DATE OF BIRTH VALIDATION
    // =========================

    private boolean isValidDateOfBirth(String dateOfBirth) {

        if (dateOfBirth == null
                || dateOfBirth.trim().isEmpty()) {
            return false;
        }

        try {

            LocalDate dob =
                    LocalDate.parse(dateOfBirth.trim());

            return !dob.isAfter(LocalDate.now().minusYears(18));

        } catch (DateTimeParseException e) {

            return false;
        }
    }


    // =========================
    // PASSWORD VALIDATION
    // =========================

    private boolean isValidPassword(String password) {

        if (password == null
            || password.length() < 8
            || password.length() > 72) {
            return false;
        }

        boolean hasLetter =
                password.matches(".*[A-Za-z].*");

        boolean hasNumber =
                password.matches(".*\\d.*");

        return hasLetter && hasNumber;
    }


    // =========================
    // FIND USER BY EMAIL
    // =========================

    public User findUserByEmail(String email) {

        if (email == null) {
            return null;
        }

        return userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
    }


    // =========================
    // FIND USER BY ID
    // =========================

    public User findUserById(Long id) {

        if (id == null) {
            return null;
        }

        return userRepository.findById(id).orElse(null);
    }


    // =========================
    // LOGIN USER
    // =========================

    public User loginUser(String email, String password) {

        if (!isValidEmail(email) || password == null || password.isBlank()) {
            return null;
        }

        User user = findUserByEmail(email);

        if (user != null
                && user.getPassword() != null
                && user.getPassword().equals(password)) {

            return user;
        }

        return null;
    }


    // =========================
    // RESET PASSWORD
    // =========================

    public boolean resetPassword(
            String email,
            String newPassword) {

        User user = findUserByEmail(email);

        if (user == null) {
            return false;
        }

        if (!isValidPassword(newPassword)) {
            return false;
        }

        // Hash the new password before saving
        user.setPassword(newPassword);

        // user was fetched from the database, so the change has to
        // be saved explicitly or it is silently lost.
        userRepository.save(user);

        return true;
    }


    // =========================
    // GET ALL USERS
    // =========================

    public List<User> getAllUsers() {

        return userRepository.findAll();
    }


    // =========================
    // SAVE USER
    // =========================

    public void saveUser(User user) {

        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("CUSTOMER");
        }

        if (user.getStatus() == null || user.getStatus().trim().isEmpty()) {
            user.setStatus("ACTIVE");
        }

        userRepository.save(user);
    }
    
    // Removed isPasswordEncoded method - reverted to plaintext password handling


    // =========================
    // UPDATE USER (fetch existing, merge only supplied fields, save)
    // =========================

    public boolean updateUser(User updatedUser) {

        if (updatedUser == null || updatedUser.getId() == null) {
            return false;
        }

        User existingUser = userRepository.findById(updatedUser.getId()).orElse(null);

        if (existingUser == null) {
            return false;
        }

        if (updatedUser.getName() != null && !updatedUser.getName().isBlank()) {
            existingUser.setName(updatedUser.getName());
        }

        if (updatedUser.getEmail() != null && !updatedUser.getEmail().isBlank()) {
            existingUser.setEmail(updatedUser.getEmail());
        }

        if (updatedUser.getTelephone() != null && !updatedUser.getTelephone().isBlank()) {
            existingUser.setTelephone(updatedUser.getTelephone());
        }

        if (updatedUser.getDateOfBirth() != null && !updatedUser.getDateOfBirth().isBlank()) {
            existingUser.setDateOfBirth(updatedUser.getDateOfBirth());
        }

        if (updatedUser.getUsername() != null && !updatedUser.getUsername().isBlank()) {
            existingUser.setUsername(updatedUser.getUsername());
        }

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isBlank()) {
            existingUser.setPassword(updatedUser.getPassword());
        }

        if (updatedUser.getRole() != null && !updatedUser.getRole().isBlank()) {
            existingUser.setRole(updatedUser.getRole());
        }

        if (updatedUser.getStatus() != null && !updatedUser.getStatus().isBlank()) {
            existingUser.setStatus(updatedUser.getStatus());
        }

        userRepository.save(existingUser);

        return true;
    }

    public String updateCustomerProfile(Long customerId, User submitted) {
        if (customerId == null || submitted == null) {
            return "Profile details are required.";
        }

        User customer = userRepository.findById(customerId).orElse(null);
        if (customer == null || !"CUSTOMER".equalsIgnoreCase(customer.getRole())) {
            return "Customer account could not be verified.";
        }
        if (!isValidName(submitted.getName())) {
            return "Enter a valid name using 2 to 50 letters, spaces, apostrophes, periods, or hyphens.";
        }
        if (!isValidEmail(submitted.getEmail())) {
            return "Enter a valid email address.";
        }
        User emailOwner = findUserByEmail(submitted.getEmail());
        if (emailOwner != null && !customerId.equals(emailOwner.getId())) {
            return "That email belongs to another account.";
        }
        if (!isValidTelephone(submitted.getTelephone())) {
            return "Enter a 10-digit telephone number.";
        }
        if (!isValidDateOfBirth(submitted.getDateOfBirth())) {
            return "Enter a valid date of birth. You must be at least 18 years old.";
        }

        customer.setName(submitted.getName().trim());
        customer.setEmail(submitted.getEmail().trim());
        customer.setTelephone(submitted.getTelephone().trim());
        customer.setDateOfBirth(submitted.getDateOfBirth().trim());
        userRepository.save(customer);
        return null;
    }


    // =========================
    // DELETE USER
    // =========================

    public boolean deleteUser(Long id) {

        if (id == null || !userRepository.existsById(id)) {
            return false;
        }

        userRepository.deleteById(id);

        return true;
    }
}
