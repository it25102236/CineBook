package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.EmailService;
import com.moviebooking.moviebookingsystem.service.SmsService;
import com.moviebooking.moviebookingsystem.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.SecureRandom;
import java.time.LocalDate;

@Controller
public class UserController {

    private final UserService userService;
    private final BookingService bookingService;
    private final EmailService emailService;
        private final SmsService smsService;
        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // How long an issued verification code stays valid. The Resend OTP
    // button issues a brand new code and an expired code must be resent.
    private static final long VERIFICATION_CODE_VALID_MINUTES = 10;

    public UserController(
            UserService userService,
            BookingService bookingService,
                        EmailService emailService,
                        SmsService smsService) {

        this.userService = userService;
        this.bookingService = bookingService;
        this.emailService = emailService;
                this.smsService = smsService;
    }


    // ================= SIGN UP =================

    @GetMapping("/signup")
    public String signupPage(Model model) {

        model.addAttribute(
                "user",
                new User()
        );
        addSignupDateLimit(model);

        return "signup";
    }


    @PostMapping("/signup")
    public String registerUser(
            @ModelAttribute User user,
            Model model,
            HttpSession session) {

        addSignupDateLimit(model);
        user.setId(null);
        user.setRole("CUSTOMER");
        user.setStatus("ACTIVE");

        User emailOwner = userService.findUserByEmail(user.getEmail());
        Long existingCustomerId = null;
        if (emailOwner != null) {
            if (!"CUSTOMER".equalsIgnoreCase(emailOwner.getRole())) {
                model.addAttribute(
                        "error",
                        "This email is already associated with a staff account."
                );
                return "signup";
            }

            if ("BLOCKED".equalsIgnoreCase(emailOwner.getStatus())) {
                model.addAttribute(
                        "error",
                        "This customer account is blocked. Please contact support."
                );
                return "signup";
            }

            existingCustomerId = emailOwner.getId();
        }

        String validationError = userService.validateCustomerRegistration(
                user,
                existingCustomerId
        );
        if (validationError != null) {
            model.addAttribute(
                    "error",
                    validationError
            );
            return "signup";
        }

        String verificationCode =
                String.format(
                        "%06d",
                        SECURE_RANDOM.nextInt(1_000_000)
                );

        session.removeAttribute("pendingUser");
        session.removeAttribute("pendingCustomerId");
        session.removeAttribute("verificationCode");
        session.removeAttribute("verificationMethod");
        session.removeAttribute("verificationExpiry");
        try {
            emailService.sendVerificationCode(
                    user.getEmail(),
                    verificationCode
            );
            session.setAttribute("verificationMethod", "EMAIL");
        } catch (Exception e) {
            String phoneNumber = emailOwner == null
                    ? user.getTelephone()
                    : emailOwner.getTelephone();
            try {
                smsService.sendVerificationCode(phoneNumber, verificationCode);
                session.setAttribute("verificationMethod", "PHONE");
            } catch (Exception smsException) {
                model.addAttribute(
                        "error",
                        "Email verification failed and phone OTP could not be sent. Check SMS configuration and try again."
                );
                return "signup";
            }
        }

        session.setAttribute("pendingUser", user);
        if (existingCustomerId != null) {
            session.setAttribute("pendingCustomerId", existingCustomerId);
        }
        session.setAttribute("verificationCode", verificationCode);
        session.setAttribute("verificationExpiry", verificationCodeExpiry());
        return "redirect:/verify-email";
    }

    private void addSignupDateLimit(Model model) {
        model.addAttribute(
                "maxDateOfBirth",
                LocalDate.now().minusYears(18).toString()
        );
    }


    // ================= VERIFY EMAIL PAGE =================

    @GetMapping("/verify-email")
    public String verifyEmailPage(
            HttpSession session,
            Model model) {

        if (session.getAttribute("pendingUser") == null) {

            return "redirect:/signup";
        }

        addVerificationContext(model, session);

        return "verify-email";
    }


    @PostMapping("/verify-email/use-phone")
    public String usePhoneVerification(
            HttpSession session,
            Model model) {

        User pendingUser = (User) session.getAttribute("pendingUser");
        if (pendingUser == null) {
            return "redirect:/signup";
        }

        String telephone = resolvePhoneDestination(session, pendingUser);
        if (telephone == null) {
            addVerificationContext(model, session);
            model.addAttribute(
                    "error",
                    "The customer account could not be verified. Please restart signup."
            );
            return "verify-email";
        }

        String verificationCode = String.format(
                "%06d",
                SECURE_RANDOM.nextInt(1_000_000)
        );
        try {
            smsService.sendVerificationCode(telephone, verificationCode);
        } catch (Exception exception) {
            addVerificationContext(model, session);
            model.addAttribute(
                    "error",
                    "Phone OTP could not be sent. Check SMS configuration and try again."
            );
            return "verify-email";
        }

        session.setAttribute("verificationCode", verificationCode);
        session.setAttribute("verificationExpiry", verificationCodeExpiry());
        session.setAttribute("verificationMethod", "PHONE");
        return "redirect:/verify-email";
    }


    // ================= RESEND VERIFICATION CODE =================

    @PostMapping("/verify-email/resend")
    public String resendVerificationCode(
            HttpSession session,
            Model model) {

        User pendingUser = (User) session.getAttribute("pendingUser");
        if (pendingUser == null) {
            return "redirect:/signup";
        }

        boolean phoneChannel =
                "PHONE".equals(session.getAttribute("verificationMethod"));

        String destination = pendingUser.getEmail();
        if (phoneChannel) {
            destination = resolvePhoneDestination(session, pendingUser);
            if (destination == null) {
                addVerificationContext(model, session);
                model.addAttribute(
                        "error",
                        "The customer account could not be verified. Please restart signup."
                );
                return "verify-email";
            }
        }

        // A brand new code is issued on every resend, so the previously
        // sent code can no longer be used.
        String verificationCode = String.format(
                "%06d",
                SECURE_RANDOM.nextInt(1_000_000)
        );

        try {
            if (phoneChannel) {
                smsService.sendVerificationCode(destination, verificationCode);
            } else {
                emailService.sendVerificationCode(destination, verificationCode);
            }
        } catch (Exception exception) {
            addVerificationContext(model, session);
            model.addAttribute(
                    "error",
                    phoneChannel
                            ? "Phone OTP could not be resent. Check SMS configuration and try again."
                            : "Verification email could not be resent. Please try again."
            );
            return "verify-email";
        }

        session.setAttribute("verificationCode", verificationCode);
        session.setAttribute("verificationExpiry", verificationCodeExpiry());
        return "redirect:/verify-email?resent=success";
    }


    // ================= VERIFY EMAIL =================

    @PostMapping("/verify-email")
    public String verifyEmail(
            @RequestParam(value = "code", required = false) String code,
            HttpSession session,
            Model model) {

        addVerificationContext(model, session);

        String correctCode =
                (String) session.getAttribute(
                        "verificationCode"
                );


        User pendingUser =
                (User) session.getAttribute(
                        "pendingUser"
                );


        // Check verification session
        if (correctCode == null
                || pendingUser == null) {

            model.addAttribute(
                    "error",
                    "Verification session expired. Please sign up again."
            );

            return "verify-email";
        }


        // The code must be the 6 digits that were issued
        String submittedCode = code == null ? "" : code.trim();
        if (!submittedCode.matches("\\d{6}")) {

            model.addAttribute(
                    "error",
                    "Enter the 6-digit verification code that was sent to you."
            );

            return "verify-email";
        }


        // An issued code only stays valid for a limited time
        Long codeExpiry =
                (Long) session.getAttribute("verificationExpiry");
        if (codeExpiry != null
                && System.currentTimeMillis() > codeExpiry) {

            model.addAttribute(
                    "error",
                    "Your verification code has expired. Please use Resend OTP to get a new code."
            );

            return "verify-email";
        }


        // Check verification code
        if (!correctCode.equals(submittedCode)) {

            model.addAttribute(
                    "error",
                    "Invalid verification code. Please try again."
            );

            return "verify-email";
        }


        // ================= ACCOUNT VERIFIED =================

        Long existingCustomerId =
                (Long) session.getAttribute("pendingCustomerId");
        boolean registered = userService.registerCustomer(
                pendingUser,
                existingCustomerId
        );


        if (!registered) {

            model.addAttribute(
                    "error",
                    "Registration could not be completed. Please restart signup and check your details."
            );

            return "verify-email";
        }


        // Remove temporary data
        session.removeAttribute(
                "pendingUser"
        );

        session.removeAttribute(
                "verificationCode"
        );
        session.removeAttribute("pendingCustomerId");
        session.removeAttribute("verificationMethod");
        session.removeAttribute("verificationExpiry");


        // Signup successful
        return "redirect:/login?signup=success";
    }

    private void addVerificationContext(Model model, HttpSession session) {
        String verificationMethod =
                (String) session.getAttribute("verificationMethod");
        model.addAttribute(
                "verificationChannel",
                "PHONE".equals(verificationMethod)
                        ? "phone number"
                        : "email address"
        );

        // A customer re-registering with their registered email keeps the
        // same account, so the page explains what the new code is for.
        if (session.getAttribute("pendingCustomerId") != null) {
            model.addAttribute(
                    "reuseNotice",
                    "This email is already registered on MovieBook. "
                            + "Enter the new verification code we sent to update your account details."
            );
        }

        // Exposed to the page so the OTP countdown timer can show how long
        // the code stays valid and keep the Resend OTP button locked until
        // the cool-down elapses.
        Long expiry =
                (Long) session.getAttribute("verificationExpiry");
        model.addAttribute(
                "verificationExpiry",
                expiry != null && expiry > 0 ? expiry : 0L
        );
    }

    /**
     * Expiry timestamp (epoch millis) for a freshly issued code.
     */
    private long verificationCodeExpiry() {
        return System.currentTimeMillis()
                + VERIFICATION_CODE_VALID_MINUTES * 60_000L;
    }

    /**
     * Phone number an OTP must be sent to. A customer who reuses their
     * registered email keeps receiving codes on their registered phone.
     *
     * @return the destination number, or null when the stored customer
     *         account can no longer be used for verification
     */
    private String resolvePhoneDestination(HttpSession session, User pendingUser) {

        Long existingCustomerId =
                (Long) session.getAttribute("pendingCustomerId");

        if (existingCustomerId == null) {
            return pendingUser.getTelephone();
        }

        User existingCustomer = userService.findUserById(existingCustomerId);
        if (existingCustomer == null
                || !"CUSTOMER".equalsIgnoreCase(existingCustomer.getRole())) {
            return null;
        }

        return existingCustomer.getTelephone();
    }


    // ================= LOGIN =================

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String redirect,
            Model model) {

        model.addAttribute(
                "user",
                new User()
        );
        model.addAttribute("redirectTo", customerReturnPath(redirect));

        return "login";
    }


    @PostMapping("/login")
    public String loginUser(
            @ModelAttribute User user,
            @RequestParam(required = false) String redirect,
            Model model,
            HttpSession session) {

        User loggedInUser =
                userService.loginUser(
                        user.getEmail(),
                        user.getPassword()
                );


        // Login failed
        if (loggedInUser == null) {

            user.setPassword(null);
            model.addAttribute(
                    "error",
                    "Invalid email or password."
            );
            model.addAttribute("redirectTo", customerReturnPath(redirect));

            return "login";
        }


        // Check account status
        if ("BLOCKED".equalsIgnoreCase(loggedInUser.getStatus())) {

            user.setPassword(null);
            model.addAttribute(
                    "error",
                    "Invalid email or password."
            );
            model.addAttribute("redirectTo", customerReturnPath(redirect));

            return "login";
        }

        User authenticatedCustomer = safeProfileFields(loggedInUser);
        authenticatedCustomer.setRole(loggedInUser.getRole());
        authenticatedCustomer.setStatus(loggedInUser.getStatus());

        // Login success
        session.setAttribute(
                "loggedInUser",
                authenticatedCustomer
        );


        session.setAttribute(
                "userName",
                loggedInUser.getName()
        );


        session.setAttribute(
                "userEmail",
                loggedInUser.getEmail()
        );


        // ================= ROLE-BASED REDIRECT =================
        // Send each staff role straight to their own dashboard so
        // every role can demo their own functionality end-to-end.

        String role =
                loggedInUser.getRole() == null
                        ? "CUSTOMER"
                        : loggedInUser.getRole().trim().toUpperCase();

        switch (role) {

            case "CASHIER":
                return "redirect:/cashier";

            case "THEATRE_MANAGER":
                return "redirect:/theatre-manager";

            case "ADMIN":
                return "redirect:/admin";

            case "OWNER":
                return "redirect:/owner";

            case "SUPPORT":
                return "redirect:/support";

            case "MARKETING":
                return "redirect:/marketing";

            default:
                                String returnPath = customerReturnPath(redirect);
                                if (returnPath != null) {
                                        return "redirect:" + returnPath;
                                }
                return "redirect:/?login=success";
        }
    }

        private String customerReturnPath(String requestedPath) {
                if ("/booking".equals(requestedPath)
                                || "/my-bookings".equals(requestedPath)) {
                        return requestedPath;
                }
                return null;
        }


    // ================= LOGOUT =================

    @GetMapping("/logout")
    public String logout(
            HttpSession session) {

        session.invalidate();

        return "redirect:/?logout=success";
    }


    // ================= FORGOT PASSWORD =================

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(
            Model model) {

        model.addAttribute(
                "user",
                new User()
        );

        return "forgot-password";
    }


    @PostMapping("/forgot-password")
    public String resetPassword(
            @ModelAttribute User user,
            Model model) {

        boolean reset =
                userService.resetPassword(
                        user.getEmail(),
                        user.getPassword()
                );


        if (!reset) {

            model.addAttribute(
                    "error",
                    "Email not found or password is invalid."
            );

            return "forgot-password";
        }


        return "redirect:/login?reset=success";
    }


    // ================= CUSTOMER PROFILE =================

    @GetMapping("/profile")
    public String profilePage(
            HttpSession session,
            Model model) {

        // Check whether customer is logged in
        User loggedInUser =
                (User) session.getAttribute(
                        "loggedInUser"
                );


        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (!"CUSTOMER".equalsIgnoreCase(loggedInUser.getRole())) {
            return "redirect:/";
        }

        User profile = userService.findUserById(loggedInUser.getId());
        if (profile == null || !"CUSTOMER".equalsIgnoreCase(profile.getRole())) {
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute(
                "user",
                safeProfileFields(profile)
        );
        addSignupDateLimit(model);


        return "profile";
    }


    // ================= UPDATE CUSTOMER PROFILE =================

    @PostMapping("/profile")
    @Transactional
    public String updateProfile(
            @ModelAttribute User user,
            HttpSession session,
            Model model) {

        // Get logged-in customer
        User loggedInUser =
                (User) session.getAttribute(
                        "loggedInUser"
                );


        if (loggedInUser == null) {

            return "redirect:/login";
        }
        if (!"CUSTOMER".equalsIgnoreCase(loggedInUser.getRole())) {
            return "redirect:/";
        }

        String oldEmail = loggedInUser.getEmail();
        String validationError = userService.updateCustomerProfile(
                loggedInUser.getId(),
                user
        );
        if (validationError != null) {
            user.setId(loggedInUser.getId());
            model.addAttribute(
                    "error",
                    validationError
            );
            model.addAttribute("user", safeProfileFields(user));
            addSignupDateLimit(model);
            return "profile";
        }

        User updatedCustomer = userService.findUserById(loggedInUser.getId());
        if (updatedCustomer == null) {
            return "redirect:/profile?error=true";
        }
        if (oldEmail != null && !oldEmail.equalsIgnoreCase(updatedCustomer.getEmail())) {
            bookingService.updateCustomerEmail(oldEmail, updatedCustomer.getEmail());
        }

        User authenticatedCustomer = safeProfileFields(updatedCustomer);
        authenticatedCustomer.setRole(updatedCustomer.getRole());
        authenticatedCustomer.setStatus(updatedCustomer.getStatus());
        session.setAttribute(
                "loggedInUser",
                authenticatedCustomer
        );
        session.setAttribute(
                "userName",
                updatedCustomer.getName()
        );
        session.setAttribute(
                "userEmail",
                updatedCustomer.getEmail()
        );


        return "redirect:/profile?success=true";
    }

    private User safeProfileFields(User source) {
        User profile = new User();
        profile.setId(source.getId());
        profile.setName(source.getName());
        profile.setEmail(source.getEmail());
        profile.setTelephone(source.getTelephone());
        profile.setDateOfBirth(source.getDateOfBirth());
        profile.setUsername(source.getUsername());
        profile.setRole(source.getRole());
        profile.setStatus(source.getStatus());
        return profile;
    }
}