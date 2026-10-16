package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.SupportTicket;
import com.moviebooking.moviebookingsystem.model.TicketAttachment;
import com.moviebooking.moviebookingsystem.repository.TicketAttachmentRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TicketAttachmentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "application/pdf");

    private final TicketAttachmentRepository attachmentRepository;

    @Value("${app.support.attachment-dir:./support-attachments}")
    private String attachmentDir;

    private Path storageDirectory;

    public TicketAttachmentService(TicketAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    @PostConstruct
    void initStorage() {
        this.storageDirectory =
                Paths.get(attachmentDir).toAbsolutePath().normalize();
    }

    // Validate attachment size and actual content type without storing it
    public void validateAttachment(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Attachment file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Attachment must be at most 5 MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }

        // Validate the actual content bytes - the declared
        // content type is never trusted
        String contentType = sniffContentType(content);
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported or unrecognised file content. Allowed: JPG, PNG, PDF.");
        }
    }

    // Store an uploaded attachment after validating type and size
    public TicketAttachment store(
            MultipartFile file,
            SupportTicket ticket,
            Long uploadedBy,
            String uploadedByName) {

        if (ticket == null || ticket.getId() == null) {
            throw new IllegalArgumentException("Ticket is required");
        }

        validateAttachment(file);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }

        String contentType = sniffContentType(content);
        if (contentType == null) {
            throw new IllegalArgumentException(
                    "Unsupported or unrecognised file content. Allowed: JPG, PNG, PDF.");
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "-" + originalName;

        Path target = storageDirectory.resolve(storedName).normalize();
        if (!target.startsWith(storageDirectory.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Invalid attachment path");
        }

        try {
            Files.createDirectories(storageDirectory);
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not store the attachment");
        }

        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicket(ticket);
        attachment.setFileName(originalName);
        attachment.setStoredFileName(storedName);
        attachment.setContentType(contentType);
        attachment.setFileSize((long) content.length);
        attachment.setUploadedBy(uploadedBy);
        attachment.setUploadedByName(uploadedByName);
        attachment.setUploadedAt(System.currentTimeMillis());

        attachmentRepository.save(attachment);
        return attachment;
    }

    // Detect the real content type from the file magic bytes.
    // Returns null when the content cannot be identified.
    private String sniffContentType(byte[] content) {

        if (content == null || content.length < 4) {
            return null;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((content[0] & 0xFF) == 0x89
                && (content[1] & 0xFF) == 0x50
                && (content[2] & 0xFF) == 0x4E
                && (content[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PDF: %PDF
        if (content[0] == 0x25
                && content[1] == 0x50
                && content[2] == 0x44
                && content[3] == 0x46) {
            return "application/pdf";
        }

        // Fall back to the JDK magic-byte detector
        try {
            return URLConnection.guessContentTypeFromStream(
                    new ByteArrayInputStream(content));
        } catch (IOException e) {
            return null;
        }
    }

    // Strip directory separators and suspicious characters from a filename
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment";
        }
        String clean = fileName.replace('\\', '/');
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }
        clean = clean.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.isBlank() ? "attachment" : clean;
    }

    // Read the stored bytes of an attachment from the secure storage directory
    public byte[] readStoredFile(TicketAttachment attachment) throws IOException {

        if (attachment == null || attachment.getStoredFileName() == null) {
            throw new IOException("Attachment file is missing");
        }

        Path stored = storageDirectory
                .resolve(attachment.getStoredFileName())
                .normalize();

        if (!stored.startsWith(storageDirectory)) {
            throw new IOException("Invalid attachment path");
        }

        return Files.readAllBytes(stored);
    }

    // Get one attachment by ID
    public TicketAttachment getAttachment(Long id) {

        if (id == null) {
            return null;
        }

        return attachmentRepository.findById(id).orElse(null);
    }

    // List attachments for a ticket, oldest first
    public List<TicketAttachment> getAttachmentsForTicket(Long ticketId) {

        if (ticketId == null) {
            return List.of();
        }

        return attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId);
    }

    // Delete all attachments of a ticket (records and stored files)
    @Transactional
    public void deleteAttachmentsForTicket(Long ticketId) {

        if (ticketId == null) {
            return;
        }

        List<TicketAttachment> attachments =
                attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticketId);

        for (TicketAttachment attachment : attachments) {
            attachmentRepository.delete(attachment);
            deleteStoredFile(attachment);
        }
    }

    // Remove the stored file of an attachment from the secure directory
    private void deleteStoredFile(TicketAttachment attachment) {

        if (attachment == null || attachment.getStoredFileName() == null) {
            return;
        }

        Path stored = storageDirectory
                .resolve(attachment.getStoredFileName())
                .normalize();

        if (stored.startsWith(storageDirectory)) {
            try {
                Files.deleteIfExists(stored);
            } catch (IOException ignored) {
                // Record is already removed; leftover file is harmless
            }
        }
    }

    // Delete an attachment record and its stored file
    @Transactional
    public boolean deleteAttachment(Long id) {
        if (id == null) {
            return false;
        }

        Optional<TicketAttachment> existing = attachmentRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }

        TicketAttachment attachment = existing.get();
        attachmentRepository.delete(attachment);
        deleteStoredFile(attachment);

        return true;
    }
}
