package com.reviewflow.service;
import com.reviewflow.util.HashidService;

import com.reviewflow.event.AnnouncementPublishedEvent;
import com.reviewflow.exception.AccessDeniedException;
import com.reviewflow.exception.AlreadyPublishedException;
import com.reviewflow.exception.AnnouncementNotFoundException;
import com.reviewflow.exception.CourseNotOwnedException;
import com.reviewflow.model.entity.*;
import com.reviewflow.model.enums.AnnouncementTarget;
import com.reviewflow.model.enums.RecipientType;
import com.reviewflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseInstructorRepository courseInstructorRepository;
    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private HashidService hashidService;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AnnouncementService announcementService;

    private User instructor;
    private User admin;
    private User student;
    private Course course;
    private Announcement announcement;

    @BeforeEach
    void setUp() {
        instructor = User.builder().id(10L).email("instructor@test.com").firstName("Prof").lastName("Smith").build();
        admin = User.builder().id(20L).email("admin@test.com").firstName("Admin").lastName("User").role(UserRole.ADMIN).build();
        student = User.builder().id(30L).email("student@test.com").firstName("Jane").lastName("Doe").build();
        course = Course.builder().id(100L).code("CS101").build();
        announcement = Announcement.builder()
                .id(1000L)
                .course(course)
                .createdBy(instructor)
                .title("Test Announcement")
                .body("Test body")
                .isPublished(false)
                .build();
    }

    // ── CREATE COURSE ANNOUNCEMENT ───────────────────────────────
    @Test
    void createCourseAnnouncement_instructorOwnsCoursse_succeeds() {
        when(hashidService.decodeOrThrow("course_hash")).thenReturn(100L);
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(100L, 10L)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(instructor));
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> {
            Announcement arg = inv.getArgument(0);
            arg.setId(1000L);
            return arg;
        });

        Announcement result = announcementService.createCourseAnnouncement("course_hash", 10L, "Title", "Body");

        assertNotNull(result);
        assertEquals("Title", result.getTitle());
        assertEquals("Body", result.getBody());
        assertEquals(AnnouncementTarget.COURSE, result.getTarget());
        assertFalse(result.getIsPublished());
        verify(announcementRepository, times(1)).save(any(Announcement.class));
        verify(auditService, times(1)).log(eq(10L), eq("ANNOUNCEMENT_CREATED"), any(), any(), any(), any());
    }

    @Test
    void createCourseAnnouncement_instructorDoesNotOwnCourse_throws403() {
        when(hashidService.decodeOrThrow("course_hash")).thenReturn(100L);
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(100L, 10L)).thenReturn(false);

        assertThrows(CourseNotOwnedException.class,
                () -> announcementService.createCourseAnnouncement("course_hash", 10L, "Title", "Body"));

        verify(auditService, times(1)).log(eq(10L), eq("ANNOUNCEMENT_CREATE_DENIED"), any(), any(), any(), any());
    }

    // ── CREATE PLATFORM ANNOUNCEMENT ───────────────────────────────
    @Test
    void createPlatformAnnouncement_adminRole_succeeds() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        Announcement result = announcementService.createPlatformAnnouncement(20L, "Platform Title", "Platform body", RecipientType.ALL_STUDENTS);

        assertNotNull(result);
        assertEquals(AnnouncementTarget.PLATFORM, result.getTarget());
        assertEquals(RecipientType.ALL_STUDENTS, result.getRecipientType());
        assertNull(result.getCourse());
    }

    @Test
    void createPlatformAnnouncement_nonAdminRole_throws403() {
        when(userRepository.findById(30L)).thenReturn(Optional.of(student));

        assertThrows(AccessDeniedException.class,
                () -> announcementService.createPlatformAnnouncement(30L, "Title", "Body", RecipientType.ALL_STUDENTS));
    }

    // ── PUBLISH ANNOUNCEMENT ───────────────────────────────
    @Test
    void publish_creatorPublishesOwnDraft_succeeds() {
        when(announcementRepository.findById(1000L)).thenReturn(Optional.of(announcement));
        when(userRepository.findById(10L)).thenReturn(Optional.of(instructor));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);

        Announcement result = announcementService.publish(1000L, 10L);

        assertTrue(result.getIsPublished());
        assertNotNull(result.getPublishedAt());

        ArgumentCaptor<AnnouncementPublishedEvent> captor = ArgumentCaptor.forClass(AnnouncementPublishedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals(1000L, captor.getValue().getAnnouncementId());
    }

    @Test
    void publish_alreadyPublished_throwsConflict() {
        announcement.setIsPublished(true);
        announcement.setPublishedAt(Instant.now());
        when(announcementRepository.findById(1000L)).thenReturn(Optional.of(announcement));
        when(userRepository.findById(10L)).thenReturn(Optional.of(instructor));

        assertThrows(AlreadyPublishedException.class,
                () -> announcementService.publish(1000L, 10L));
    }

    @Test
    void publish_nonOwnerNonAdmin_throws403() {
        when(announcementRepository.findById(1000L)).thenReturn(Optional.of(announcement));
        when(userRepository.findById(30L)).thenReturn(Optional.of(student));

        assertThrows(AccessDeniedException.class,
                () -> announcementService.publish(1000L, 30L));
    }

    @Test
    void publish_adminCanPublishOtherUserContent_succeeds() {
        when(announcementRepository.findById(1000L)).thenReturn(Optional.of(announcement));
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(announcementRepository.save(any(Announcement.class))).thenReturn(announcement);

        Announcement result = announcementService.publish(1000L, 20L);

        assertTrue(result.getIsPublished());
    }

    // ── DELETE ANNOUNCEMENT ───────────────────────────────
    @Test
    void delete_creatorDeletes_succeeds() {
        when(announcementRepository.findById(1000L)).thenReturn(Optional.of(announcement));
        when(userRepository.findById(10L)).thenReturn(Optional.of(instructor));

        announcementService.delete(1000L, 10L);

        verify(announcementRepository, times(1)).deleteById(1000L);
        verify(auditService, times(1)).log(eq(10L), eq("ANNOUNCEMENT_DELETED"), any(), any(), any(), any());
    }

    @Test
    void delete_nonOwnerNonAdmin_throws403() {
        when(announcementRepository.findById(1000L)).thenReturn(Optional.of(announcement));
        when(userRepository.findById(30L)).thenReturn(Optional.of(student));

        assertThrows(AccessDeniedException.class,
                () -> announcementService.delete(1000L, 30L));
    }

    // ── GET BY COURSE ───────────────────────────────
    @Test
    void getByCourse_enrolledStudentCanViewPublished() {
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(100L, 30L)).thenReturn(true);
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(100L, 30L)).thenReturn(false);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Announcement> page = new PageImpl<>(List.of(announcement), pageable, 1);
        when(announcementRepository.findByCourse_IdAndIsPublishedTrue(100L, pageable)).thenReturn(page);

        Page<Announcement> result = announcementService.getByCourse(100L, 30L, pageable);

        assertEquals(1, result.getTotalElements());
        verify(announcementRepository, times(1)).findByCourse_IdAndIsPublishedTrue(100L, pageable);
    }

    @Test
    void getByCourse_instructorCanViewPublished() {
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(100L, 10L)).thenReturn(false);
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(100L, 10L)).thenReturn(true);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Announcement> page = new PageImpl<>(List.of(announcement), pageable, 1);
        when(announcementRepository.findByCourse_IdAndIsPublishedTrue(100L, pageable)).thenReturn(page);

        Page<Announcement> result = announcementService.getByCourse(100L, 10L, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getByCourse_notEnrolledNorInstructor_throws403() {
        when(courseEnrollmentRepository.existsByCourse_IdAndUser_Id(100L, 30L)).thenReturn(false);
        when(courseInstructorRepository.existsByCourse_IdAndUser_Id(100L, 30L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> announcementService.getByCourse(100L, 30L, PageRequest.of(0, 20)));
    }
}
