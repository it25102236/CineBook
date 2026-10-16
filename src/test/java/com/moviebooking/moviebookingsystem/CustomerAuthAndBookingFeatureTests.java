package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.repository.TheatreRepository;
import com.moviebooking.moviebookingsystem.repository.UserRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.EmailService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.SmsService;
import com.moviebooking.moviebookingsystem.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration coverage for the three customer features:
 *
 * 1. A registered customer email can be reused for login and OTP
 *    verification (the existing account is updated, never duplicated).
 * 2. The verify page offers a Resend OTP button that issues a brand new
 *    code, and the entered code is properly validated (format, expiry,
 *    match).
 * 3. The booking page carries the location of each theatre so it can be
 *    displayed below the theatre selection.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerAuthAndBookingFeatureTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TheatreRepository theatreRepository;

    @Autowired
    private UserService userService;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private SmsService smsService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private MovieService movieService;

    @MockitoBean
    private ShowtimeService showtimeService;

    @MockitoBean
    private RefundService refundService;

    @MockitoBean
    private PaymentRepository paymentRepository;

    private User seededCustomer;
    private Theatre seededTheatre;

    @AfterEach
    void cleanUpSeededRows() {
        if (seededCustomer != null) {
            userRepository.deleteById(seededCustomer.getId());
            seededCustomer = null;
        }
        if (seededTheatre != null) {
            theatreRepository.deleteById(seededTheatre.getId());
            seededTheatre = null;
        }
    }


    // ==================================================================
    // 1 + 2: REGISTERED EMAIL REUSE, OTP RESEND AND OTP VALIDATION
    // ==================================================================

    @Test
    void registeredEmailCanBeReusedForOtpVerificationAndLogin() throws Exception {

        seededCustomer = userRepository.saveAndFlush(customer(
                "reuse@example.com",
                "reuse_customer",
                "Oldpass123",
                "0771234567"));

        // Every OTP the controller sends to the registered email is recorded
        List<String> sentCodes = new ArrayList<>();
        doAnswer(invocation -> {
            sentCodes.add(invocation.getArgument(1));
            return null;
        }).when(emailService).sendVerificationCode(
                eq("reuse@example.com"), anyString());

        MockHttpSession session = new MockHttpSession();

        // ---- Sign up again with the already registered email ----

        mockMvc.perform(post("/signup")
                .param("name", "Reuse Customer")
                .param("email", "reuse@example.com")
                .param("telephone", "0777654321")
                .param("dateOfBirth", LocalDate.now().minusYears(25).toString())
                .param("username", "reuse_customer_updated")
                .param("password", "Newpass123")
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-email"));

        // The OTP went to the registered email and the existing account is kept
        assertEquals(1, sentCodes.size());
        String initialCode = sentCodes.get(0);
        assertTrue(initialCode.matches("\\d{6}"),
                "The OTP must be a 6 digit code");

        Long pendingCustomerId =
                (Long) session.getAttribute("pendingCustomerId");
        assertNotNull(pendingCustomerId,
                "Re-registration must reuse the existing customer account");
        assertEquals(seededCustomer.getId(), pendingCustomerId);
        assertTrue(session.getAttribute("pendingUser") instanceof User);

        // ---- A wrong code is rejected ----

        String wrongCode = initialCode.equals("000000") ? "111111" : "000000";
        mockMvc.perform(post("/verify-email")
                .param("code", wrongCode)
                .session(session))
                .andExpect(view().name("verify-email"))
                .andExpect(model().attribute(
                        "error",
                        "Invalid verification code. Please try again."));

        // ---- Resend OTP issues a brand new code ----

        mockMvc.perform(post("/verify-email/resend")
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-email?resent=success"));

        assertEquals(2, sentCodes.size(),
                "Resend OTP must send another code to the registered email");
        String resentCode = sentCodes.get(1);
        assertNotEquals(initialCode, resentCode,
                "Resend OTP must issue a new code");

        // ---- The previously sent code can no longer be used ----

        mockMvc.perform(post("/verify-email")
                .param("code", initialCode)
                .session(session))
                .andExpect(view().name("verify-email"))
                .andExpect(model().attribute(
                        "error",
                        "Invalid verification code. Please try again."));

        // ---- The new code verifies the account ----

        mockMvc.perform(post("/verify-email")
                .param("code", resentCode)
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?signup=success"));

        // ---- The registered account is updated, never duplicated ----

        User updated = userRepository.findById(seededCustomer.getId())
                .orElseThrow();
        assertEquals("Reuse Customer", updated.getName());
        assertEquals("reuse_customer_updated", updated.getUsername());
        assertEquals("Newpass123", updated.getPassword());
        assertEquals("0777654321", updated.getTelephone());
        assertEquals("reuse@example.com", updated.getEmail());
        assertEquals("CUSTOMER", updated.getRole());
        assertEquals("ACTIVE", updated.getStatus());

        assertTrue(userRepository.findByUsernameIgnoreCase("reuse_customer")
                        .isEmpty(),
                "The previous username must be released");

        // ---- Login works with the registered email and new password ----

        assertNotNull(userService.loginUser("reuse@example.com", "Newpass123"),
                "The customer must be able to log in with the registered email");
        assertNull(userService.loginUser("reuse@example.com", "Oldpass123"),
                "The old password must no longer work after the update");
    }


    @Test
    void verificationRejectsMissingOrMalformedCode() throws Exception {

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingUser", customer(
                "malformed@example.com",
                "malformed_customer",
                "Passw0rd123",
                "0771112223"));
        session.setAttribute("verificationCode", "123456");
        session.setAttribute("verificationMethod", "EMAIL");
        session.setAttribute("verificationExpiry",
                System.currentTimeMillis() + 10 * 60_000L);

        String formatError =
                "Enter the 6-digit verification code that was sent to you.";

        // Missing code must not blow up with a 400
        mockMvc.perform(post("/verify-email")
                .session(session))
                .andExpect(view().name("verify-email"))
                .andExpect(model().attribute("error", formatError));

        // Malformed code is rejected as well
        mockMvc.perform(post("/verify-email")
                .param("code", "12ab")
                .session(session))
                .andExpect(view().name("verify-email"))
                .andExpect(model().attribute("error", formatError));

        assertTrue(userRepository
                        .findByEmailIgnoreCase("malformed@example.com")
                        .isEmpty(),
                "A rejected OTP must not create the account");
    }


    @Test
    void verificationRejectsExpiredCode() throws Exception {

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingUser", customer(
                "expired@example.com",
                "expired_customer",
                "Passw0rd123",
                "0771112223"));
        session.setAttribute("verificationCode", "123456");
        session.setAttribute("verificationMethod", "EMAIL");
        session.setAttribute("verificationExpiry",
                System.currentTimeMillis() - 60_000L);

        mockMvc.perform(post("/verify-email")
                .param("code", "123456")
                .session(session))
                .andExpect(view().name("verify-email"))
                .andExpect(model().attribute(
                        "error",
                        "Your verification code has expired. "
                                + "Please use Resend OTP to get a new code."));

        assertTrue(userRepository
                        .findByEmailIgnoreCase("expired@example.com")
                        .isEmpty(),
                "An expired OTP must not create the account");
    }


    @Test
    void resendUsesThePhoneChannelWhenVerificationWasSwitchedToPhone()
            throws Exception {

        // Record every OTP the SMS service is asked to send
        List<String> smsCodes = new ArrayList<>();
        doAnswer(invocation -> {
            smsCodes.add(invocation.getArgument(1));
            return null;
        }).when(smsService).sendVerificationCode(
                eq("0771112223"), anyString());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingUser", customer(
                "phone@example.com",
                "phone_customer",
                "Passw0rd123",
                "0771112223"));
        session.setAttribute("verificationCode", "123456");
        session.setAttribute("verificationMethod", "EMAIL");
        session.setAttribute("verificationExpiry",
                System.currentTimeMillis() + 10 * 60_000L);

        // Switch the OTP channel to the phone number
        mockMvc.perform(post("/verify-email/use-phone")
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-email"));

        assertEquals("PHONE", session.getAttribute("verificationMethod"));
        assertEquals(1, smsCodes.size(),
                "Switching to the phone channel must send one OTP");
        String phoneCode = smsCodes.get(0);
        assertNotEquals("123456", phoneCode,
                "The phone channel must issue a freshly generated code");

        // ---- Resend OTP keeps using the phone channel ----

        mockMvc.perform(post("/verify-email/resend")
                .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-email?resent=success"));

        assertEquals(2, smsCodes.size(),
                "Resend OTP must send another code on the phone channel");
        String resentPhoneCode = smsCodes.get(1);
        assertNotEquals(phoneCode, resentPhoneCode,
                "Resend OTP must issue a new code");
        assertEquals("PHONE", session.getAttribute("verificationMethod"),
                "Resend must not switch the verification channel back");
        assertNotEquals("123456",
                session.getAttribute("verificationCode"),
                "Resend OTP must store a freshly generated code");
    }


    @Test
    void resendRequiresAnActiveVerificationSession() throws Exception {

        mockMvc.perform(post("/verify-email/resend")
                .session(new MockHttpSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signup"));
    }


    // ==================================================================
    // 2 (frontend): RESEND OTP COUNTDOWN TIMER + OTP EXPIRY
    // ==================================================================

    @Test
    void verifyPageRendersOtpCountdownAndResendCooldown()
            throws Exception {

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("pendingUser", customer(
                "otp@example.com",
                "otp_customer",
                "Passw0rd123",
                "0771112223"));
        session.setAttribute("verificationCode", "123456");
        session.setAttribute("verificationMethod", "EMAIL");

        long expiry = System.currentTimeMillis() + 10 * 60_000L;
        session.setAttribute("verificationExpiry", expiry);

        String html = mockMvc.perform(get("/verify-email")
                .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // Resend OTP button and its cooldown indicator are rendered
        assertTrue(html.contains("id=\"resendOtpButton\""),
                "The Resend OTP button must render");
        assertTrue(html.contains("id=\"resendCooldown\""),
                "The resend cooldown indicator must render");
        assertTrue(html.contains("function startResendCooldown"),
                "The resend cooldown timer script must render");

        // OTP validity is shown as a countdown driven by the server expiry
        assertTrue(html.contains("id=\"otpCountdownText\""),
                "The OTP countdown text must render");
        assertTrue(html.contains("function updateOtpExpiry"),
                "The OTP expiry countdown script must render");
        assertTrue(html.contains("otpExpiryTimestamp"),
                "The OTP expiry must reach the page for the countdown");
        assertTrue(html.contains(String.valueOf(expiry)),
                "The page must carry the session OTP expiry value");
    }


    // ==================================================================
    // 3: THEATRE LOCATION SHOWN BELOW THE THEATRE SELECTION
    // ==================================================================

    @Test
    void bookingPageCarriesTheTheatreLocationForTheSelectedTheatre()
            throws Exception {

        seededTheatre = new Theatre();
        seededTheatre.setName("Location Test Hall");
        seededTheatre.setLocation("Colombo 03");
        seededTheatre.setTotalSeats(300);
        seededTheatre.setStatus(Theatre.STATUS_AVAILABLE);
        seededTheatre = theatreRepository.saveAndFlush(seededTheatre);

        when(bookingService.getAllBookings()).thenReturn(List.of());
        when(movieService.getAllMovies()).thenReturn(List.of(
                movie(1L, "The Last Journey")));
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of(
                showtime(1L, "Location Test Hall", "2099-01-10",
                        "06:30 PM", 300, 298, 1000.0)));

        String html = mockMvc.perform(get("/booking")
                .session(customerSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // The location travels with every showtime record
        assertTrue(html.contains("\"theatreLocation\":\"Colombo 03\""),
                "The showtime record must carry the theatre location");

        // The location is rendered below the theatre selection and updated
        // automatically by the page script
        assertTrue(html.contains("id=\"theatreLocationInfo\""),
                "The theatre location element must render below the theatre "
                        + "selection");
        assertTrue(html.contains("function renderTheatreLocation"),
                "The theatre location must update when a theatre is selected");

        // The existing booking flow is preserved
        assertTrue(html.contains("Confirm Booking"),
                "The booking page must keep its existing behaviour");
        assertTrue(html.contains("\"theatre\":\"Location Test Hall\""),
                "The theatre cascade must still come from saved showtimes");
    }


    @Test
    void bookingPageStillRendersWithoutAnyTheatreLocation() throws Exception {

        when(bookingService.getAllBookings()).thenReturn(List.of());
        when(movieService.getAllMovies()).thenReturn(List.of());
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of());

        String html = mockMvc.perform(get("/booking")
                .session(customerSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        // An empty location must not break the cascade
        assertTrue(html.contains("const bookingShowtimes ="),
                "Empty showtime data must still produce a usable cascade");
        assertTrue(html.contains("Select Theatre"));
    }

    @Test
    void movieSearchPageRendersFiltersAndFriendlyNoResultsMessage() throws Exception {
        when(movieService.getAllMovies()).thenReturn(List.of());
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of());

        String html = mockMvc.perform(get("/movies")
                        .param("search", "not-a-real-movie")
                        .param("genre", "")
                        .param("availability", "any"))
                .andExpect(status().isOk())
                .andExpect(view().name("movies"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("id=\"movieGenre\""));
        assertTrue(html.contains("id=\"movieAvailability\""));
        assertTrue(html.contains("No Movies Found"));
        assertTrue(html.contains("No movies matched these filters"));
    }

    @Test
    void bookingHistoryPageRendersPersistedPaymentStatusAndCustomerDetails() throws Exception {
        Booking booking = new Booking();
        booking.setId(98765L);
        booking.setMovieTitle("Customer-owned Movie");
        booking.setTheatre("Test Hall");
        booking.setDate("2099-01-10");
        booking.setShowTime("07:00 PM");
        booking.setSeatNumber("A1, A2");
        booking.setPrice(2400);
        booking.setStatus("PAID");
        booking.setCustomerEmail("feature-test@example.com");
        when(bookingService.getBookingsForCustomer("feature-test@example.com"))
                .thenReturn(List.of(booking));
        Payment payment = new Payment();
        payment.setStatus("SIMULATED");
        when(paymentRepository.findFirstByBookingIdOrderByIdDesc(98765L))
                .thenReturn(Optional.of(payment));

        String html = mockMvc.perform(get("/my-bookings")
                        .session(customerSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("my-bookings"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("Customer-owned Movie"));
        assertTrue(html.contains("A1, A2"));
        assertTrue(html.contains("SIMULATED"));
        assertTrue(html.contains("View Details"));
    }

    @Test
    void profileUpdateIgnoresSubmittedRoleStatusAndPassword() throws Exception {
        seededCustomer = userRepository.saveAndFlush(customer(
                "profile-update@example.com",
                "profile_update_customer",
                "Original123",
                "0771234567"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", seededCustomer);

        mockMvc.perform(post("/profile")
                        .session(session)
                        .param("name", "Updated Customer")
                        .param("email", "profile-update@example.com")
                        .param("telephone", "0777654321")
                        .param("dateOfBirth", LocalDate.now().minusYears(25).toString())
                        .param("role", "ADMIN")
                        .param("status", "BLOCKED")
                        .param("password", "AttackerPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?success=true"));

        User updated = userRepository.findById(seededCustomer.getId()).orElseThrow();
        assertEquals("CUSTOMER", updated.getRole());
        assertEquals("ACTIVE", updated.getStatus());
        assertEquals("Original123", updated.getPassword());
        assertEquals("Updated Customer", updated.getName());
        assertEquals("0777654321", updated.getTelephone());
    }

    @Test
    void loginUsesGenericErrorsAndDoesNotPlacePasswordInSession() throws Exception {
        seededCustomer = userRepository.saveAndFlush(customer(
                "login-validation@example.com",
                "login_validation_customer",
                "Original123",
                "0771234567"));
        MockHttpSession session = new MockHttpSession();

        String invalidResponse = mockMvc.perform(post("/login")
                        .session(session)
                        .param("email", "login-validation@example.com")
                        .param("password", "Wrong123"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("error", "Invalid email or password."))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(!invalidResponse.contains("Wrong123"));

        mockMvc.perform(post("/login")
                        .session(session)
                        .param("email", "login-validation@example.com")
                        .param("password", "Original123"))
                .andExpect(status().is3xxRedirection());

        User sessionUser = (User) session.getAttribute("loggedInUser");
        assertNotNull(sessionUser);
        assertNull(sessionUser.getPassword());
        assertEquals("Original123",
                userRepository.findById(seededCustomer.getId()).orElseThrow().getPassword());
    }

    // ==================================================================
    // HELPERS
    // ==================================================================

    private User customer(String email, String username, String password,
                          String telephone) {
        User user = new User();
        user.setName("Feature Customer");
        user.setEmail(email);
        user.setTelephone(telephone);
        user.setDateOfBirth(LocalDate.now().minusYears(25).toString());
        user.setUsername(username);
        user.setPassword(password);
        user.setRole("CUSTOMER");
        user.setStatus("ACTIVE");
        return user;
    }

    private MockHttpSession customerSession() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn("CUSTOMER");
        when(user.getEmail()).thenReturn("feature-test@example.com");
        when(user.getName()).thenReturn("Feature Test");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", user);
        return session;
    }

    private Movie movie(Long id, String title) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        return movie;
    }

    private Showtime showtime(Long movieId, String theatre, String date,
                              String showTime, int totalSeats,
                              int availableSeats, double ticketPrice) {
        Showtime showtime = new Showtime();
        showtime.setMovieId(movieId);
        showtime.setMovieTitle("The Last Journey");
        showtime.setTheatre(theatre);
        showtime.setDate(date);
        showtime.setShowTime(showTime);
        showtime.setTotalSeats(totalSeats);
        showtime.setAvailableSeats(availableSeats);
        showtime.setTicketPrice(ticketPrice);
        return showtime;
    }
}
