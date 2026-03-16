package com.bookcrossing.model;

import com.bookcrossing.service.AchievementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Model classes")
class ModelTest {

    // ─── User ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("User")
    class UserTests {

        @Test
        @DisplayName("getAvatarDisplay — null URL — default avatar")
        void avatarDisplay_null() {
            User u = new User(); u.setAvatarUrl(null);
            assertThat(u.getAvatarDisplay()).isEqualTo("/images/usual_avatar.png");
        }

        @Test
        @DisplayName("getAvatarDisplay — пустой URL — default avatar")
        void avatarDisplay_empty() {
            User u = new User(); u.setAvatarUrl("");
            assertThat(u.getAvatarDisplay()).isEqualTo("/images/usual_avatar.png");
        }

        @Test
        @DisplayName("getAvatarDisplay — с URL — возвращает URL")
        void avatarDisplay_withUrl() {
            User u = new User(); u.setAvatarUrl("data:image/png;base64,abc");
            assertThat(u.getAvatarDisplay()).isEqualTo("data:image/png;base64,abc");
        }

        @Test
        @DisplayName("isCurrentlyBlocked — blocked=false — false")
        void notBlocked() {
            User u = new User(); u.setBlocked(false);
            assertThat(u.isCurrentlyBlocked()).isFalse();
        }

        @Test
        @DisplayName("isCurrentlyBlocked — blocked=null — false")
        void blockedNull() {
            User u = new User(); u.setBlocked(null);
            assertThat(u.isCurrentlyBlocked()).isFalse();
        }

        @Test
        @DisplayName("isCurrentlyBlocked — бессрочная блокировка — true")
        void permanentBlock() {
            User u = new User(); u.setBlocked(true); u.setBlockUntil(null);
            assertThat(u.isCurrentlyBlocked()).isTrue();
        }

        @Test
        @DisplayName("isCurrentlyBlocked — срок истёк — false")
        void expiredBlock() {
            User u = new User(); u.setBlocked(true);
            u.setBlockUntil(LocalDateTime.now().minusDays(1));
            assertThat(u.isCurrentlyBlocked()).isFalse();
        }

        @Test
        @DisplayName("isCurrentlyBlocked — срок не истёк — true")
        void activeBlock() {
            User u = new User(); u.setBlocked(true);
            u.setBlockUntil(LocalDateTime.now().plusDays(5));
            assertThat(u.isCurrentlyBlocked()).isTrue();
        }

        @Test
        @DisplayName("isAdmin — ADMIN — true")
        void isAdmin_true() {
            User u = new User(); u.setRole(User.UserRole.ADMIN);
            assertThat(u.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("isAdmin — USER — false")
        void isAdmin_false() {
            User u = new User(); u.setRole(User.UserRole.USER);
            assertThat(u.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("isModerator — MODERATOR — true")
        void isModerator_mod() {
            User u = new User(); u.setRole(User.UserRole.MODERATOR);
            assertThat(u.isModerator()).isTrue();
        }

        @Test
        @DisplayName("isModerator — ADMIN — true")
        void isModerator_admin() {
            User u = new User(); u.setRole(User.UserRole.ADMIN);
            assertThat(u.isModerator()).isTrue();
        }

        @Test
        @DisplayName("isModerator — USER — false")
        void isModerator_user() {
            User u = new User(); u.setRole(User.UserRole.USER);
            assertThat(u.isModerator()).isFalse();
        }

        @Test
        @DisplayName("getAge — birthDate=null — null")
        void age_null() {
            User u = new User(); u.setBirthDate(null);
            assertThat(u.getAge()).isNull();
        }

        @Test
        @DisplayName("getAge — 25 лет назад — 25")
        void age_25() {
            User u = new User(); u.setBirthDate(LocalDate.now().minusYears(25));
            assertThat(u.getAge()).isEqualTo(25);
        }

        @Test
        @DisplayName("getAge — день рождения завтра — N-1")
        void age_birthdayTomorrow() {
            User u = new User();
            u.setBirthDate(LocalDate.now().minusYears(30).plusDays(1));
            assertThat(u.getAge()).isEqualTo(29);
        }

        @Test
        @DisplayName("prePersist — заполняет role, blocked, registeredAt")
        void prePersist_defaults() {
            User u = new User();
            u.prePersist();
            assertThat(u.getRole()).isEqualTo(User.UserRole.USER);
            assertThat(u.getBlocked()).isFalse();
            assertThat(u.getRegisteredAt()).isNotNull();
        }

        @Test
        @DisplayName("prePersist — registeredAt уже задан — не перезаписывается")
        void prePersist_preservesRegisteredAt() {
            LocalDateTime fixed = LocalDateTime.of(2020, 1, 1, 0, 0);
            User u = new User(); u.setRegisteredAt(fixed);
            u.prePersist();
            assertThat(u.getRegisteredAt()).isEqualTo(fixed);
        }

        @Test
        @DisplayName("Геттеры/сеттеры User работают")
        void gettersSetters() {
            User u = new User();
            u.setId(1L); u.setUsername("alice"); u.setEmail("a@b.com");
            u.setFullName("Алиса"); u.setCity("Москва"); u.setCountry("Россия");
            u.setGender("FEMALE"); u.setAboutMe("О себе");
            u.setSocialLinks("vk.com"); u.setFavoriteGenres("Фантастика");
            u.setBlockReason("spam"); u.setPassword("hash");

            assertThat(u.getId()).isEqualTo(1L);
            assertThat(u.getUsername()).isEqualTo("alice");
            assertThat(u.getEmail()).isEqualTo("a@b.com");
            assertThat(u.getFullName()).isEqualTo("Алиса");
            assertThat(u.getCity()).isEqualTo("Москва");
            assertThat(u.getCountry()).isEqualTo("Россия");
            assertThat(u.getGender()).isEqualTo("FEMALE");
            assertThat(u.getAboutMe()).isEqualTo("О себе");
            assertThat(u.getSocialLinks()).isEqualTo("vk.com");
            assertThat(u.getFavoriteGenres()).isEqualTo("Фантастика");
            assertThat(u.getBlockReason()).isEqualTo("spam");
            assertThat(u.getPassword()).isEqualTo("hash");
        }
    }

    // ─── Book ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Book")
    class BookTests {

        @Test
        @DisplayName("getImageDisplay — null — placeholder")
        void imageDisplay_null() {
            Book b = new Book(); b.setImageUrl(null);
            assertThat(b.getImageDisplay()).isEqualTo("https://via.placeholder.com/150?text=No+Cover");
        }

        @Test
        @DisplayName("getImageDisplay — пустая строка — placeholder")
        void imageDisplay_empty() {
            Book b = new Book(); b.setImageUrl("");
            assertThat(b.getImageDisplay()).isEqualTo("https://via.placeholder.com/150?text=No+Cover");
        }

        @Test
        @DisplayName("getImageDisplay — пробелы — возвращает как есть")
        void imageDisplay_blank() {
            Book b = new Book(); b.setImageUrl("  ");
            assertThat(b.getImageDisplay()).isEqualTo("  ");
        }

        @Test
        @DisplayName("getImageDisplay — с URL — возвращает URL")
        void imageDisplay_withUrl() {
            Book b = new Book(); b.setImageUrl("data:image/jpeg;base64,xyz");
            assertThat(b.getImageDisplay()).isEqualTo("data:image/jpeg;base64,xyz");
        }

        @Test
        @DisplayName("BookStatus.FREE displayValue")
        void freeStatus() {
            assertThat(Book.BookStatus.FREE.getDisplayValue()).isEqualTo("Свободна");
        }

        @Test
        @DisplayName("BookStatus.BUSY displayValue")
        void busyStatus() {
            assertThat(Book.BookStatus.BUSY.getDisplayValue()).isEqualTo("Занята");
        }

        @Test
        @DisplayName("BookStatus.BOOKED displayValue")
        void bookedStatus() {
            assertThat(Book.BookStatus.BOOKED.getDisplayValue()).isEqualTo("Забронирована");
        }

        @Test
        @DisplayName("Геттеры/сеттеры Book работают")
        void gettersSetters() {
            User owner = new User(); owner.setId(1L);
            Book b = new Book();
            b.setId(10L); b.setTitle("Война и мир"); b.setAuthor("Толстой");
            b.setDescription("Описание"); b.setGenre("FICTION");
            b.setOwner(owner); b.setStatus(Book.BookStatus.FREE);

            assertThat(b.getId()).isEqualTo(10L);
            assertThat(b.getTitle()).isEqualTo("Война и мир");
            assertThat(b.getAuthor()).isEqualTo("Толстой");
            assertThat(b.getDescription()).isEqualTo("Описание");
            assertThat(b.getGenre()).isEqualTo("FICTION");
            assertThat(b.getOwner()).isEqualTo(owner);
            assertThat(b.getStatus()).isEqualTo(Book.BookStatus.FREE);
        }
    }

    // ─── Booking ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Booking")
    class BookingTests {

        @Test
        @DisplayName("prePersist — requestedAt устанавливается")
        void prePersist_sets() {
            Booking b = new Booking(); b.prePersist();
            assertThat(b.getRequestedAt()).isNotNull();
        }

        @Test
        @DisplayName("prePersist — не перезаписывает существующий requestedAt")
        void prePersist_preserves() {
            LocalDateTime fixed = LocalDateTime.of(2024, 1, 15, 10, 0);
            Booking b = new Booking(); b.setRequestedAt(fixed); b.prePersist();
            assertThat(b.getRequestedAt()).isEqualTo(fixed);
        }

        @Test
        @DisplayName("Все BookingStatus имеют displayName")
        void allStatuses() {
            for (Booking.BookingStatus s : Booking.BookingStatus.values()) {
                assertThat(s.getDisplayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("Геттеры/сеттеры Booking работают")
        void gettersSetters() {
            User owner = new User(); owner.setId(1L);
            User req   = new User(); req.setId(2L);
            Book book  = new Book(); book.setId(5L);
            LocalDateTime until = LocalDateTime.now().plusDays(7);
            LocalDateTime resp  = LocalDateTime.now();

            Booking b = new Booking();
            b.setId(1L); b.setBook(book); b.setOwner(owner); b.setRequester(req);
            b.setMessage("Хочу прочитать"); b.setOwnerResponse("Приходите в среду");
            b.setStatus(Booking.BookingStatus.PENDING);
            b.setBookedUntil(until); b.setRespondedAt(resp);

            assertThat(b.getId()).isEqualTo(1L);
            assertThat(b.getBook()).isEqualTo(book);
            assertThat(b.getOwner()).isEqualTo(owner);
            assertThat(b.getRequester()).isEqualTo(req);
            assertThat(b.getMessage()).isEqualTo("Хочу прочитать");
            assertThat(b.getOwnerResponse()).isEqualTo("Приходите в среду");
            assertThat(b.getStatus()).isEqualTo(Booking.BookingStatus.PENDING);
            assertThat(b.getBookedUntil()).isEqualTo(until);
            assertThat(b.getRespondedAt()).isEqualTo(resp);
        }
    }

    // ─── ModerationLog ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ModerationLog")
    class ModerationLogTests {

        @Test
        @DisplayName("Все ActionType имеют displayName")
        void allActionTypes() {
            for (ModerationLog.ActionType t : ModerationLog.ActionType.values()) {
                assertThat(t.getDisplayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("Геттеры/сеттеры ModerationLog работают")
        void gettersSetters() {
            User mod = new User(); mod.setId(1L);
            User target = new User(); target.setId(2L);
            LocalDateTime now = LocalDateTime.now();

            ModerationLog log = new ModerationLog();
            log.setId(1L); log.setModerator(mod); log.setTargetUser(target);
            log.setAction(ModerationLog.ActionType.USER_BLOCKED);
            log.setBookId(10L); log.setBookTitle("Книга");
            log.setReason("Нарушение"); log.setCreatedAt(now);

            assertThat(log.getId()).isEqualTo(1L);
            assertThat(log.getModerator()).isEqualTo(mod);
            assertThat(log.getTargetUser()).isEqualTo(target);
            assertThat(log.getAction()).isEqualTo(ModerationLog.ActionType.USER_BLOCKED);
            assertThat(log.getBookId()).isEqualTo(10L);
            assertThat(log.getBookTitle()).isEqualTo("Книга");
            assertThat(log.getReason()).isEqualTo("Нарушение");
            assertThat(log.getCreatedAt()).isEqualTo(now);
        }
    }

    // ─── BookGenre ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BookGenre")
    class BookGenreTests {

        @Test
        @DisplayName("Все жанры имеют displayValue")
        void allGenres() {
            for (BookGenre g : BookGenre.values()) {
                assertThat(g.getDisplayValue()).isNotBlank();
            }
        }

        @Test
        @DisplayName("FICTION — Художественная литература")
        void fiction() {
            assertThat(BookGenre.FICTION.getDisplayValue()).isEqualTo("Художественная литература");
        }

        @Test
        @DisplayName("FANTASY — Фантастика")
        void fantasy() {
            assertThat(BookGenre.FANTASY.getDisplayValue()).isEqualTo("Фантастика");
        }

        @Test
        @DisplayName("8 жанров в enum")
        void count() {
            assertThat(BookGenre.values()).hasSize(8);
        }
    }

    // ─── Complaint ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Complaint")
    class ComplaintTests {

        @Test
        @DisplayName("Все ComplaintType имеют displayName")
        void allTypes() {
            for (Complaint.ComplaintType t : Complaint.ComplaintType.values()) {
                assertThat(t.getDisplayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("Все ComplaintStatus имеют displayName")
        void allStatuses() {
            for (Complaint.ComplaintStatus s : Complaint.ComplaintStatus.values()) {
                assertThat(s.getDisplayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("SPAM — Спам")
        void spam() {
            assertThat(Complaint.ComplaintType.SPAM.getDisplayName()).isEqualTo("Спам");
        }

        @Test
        @DisplayName("PENDING — На рассмотрении")
        void pending() {
            assertThat(Complaint.ComplaintStatus.PENDING.getDisplayName()).isEqualTo("На рассмотрении");
        }

        @Test
        @DisplayName("Геттеры/сеттеры Complaint работают")
        void gettersSetters() {
            User author = new User(); author.setId(1L);
            User resolver = new User(); resolver.setId(2L);
            LocalDateTime now = LocalDateTime.now();

            Complaint c = new Complaint();
            c.setId(1L); c.setAuthor(author); c.setTargetBookId(10L);
            c.setTargetBookTitle("Книга"); c.setType(Complaint.ComplaintType.SPAM);
            c.setDescription("Описание"); c.setStatus(Complaint.ComplaintStatus.PENDING);
            c.setModeratorComment("Комментарий"); c.setResolvedBy(resolver);
            c.setCreatedAt(now); c.setResolvedAt(now);

            assertThat(c.getId()).isEqualTo(1L);
            assertThat(c.getAuthor()).isEqualTo(author);
            assertThat(c.getTargetBookId()).isEqualTo(10L);
            assertThat(c.getType()).isEqualTo(Complaint.ComplaintType.SPAM);
            assertThat(c.getStatus()).isEqualTo(Complaint.ComplaintStatus.PENDING);
            assertThat(c.getModeratorComment()).isEqualTo("Комментарий");
            assertThat(c.getResolvedBy()).isEqualTo(resolver);
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Notification")
    class NotificationTests {

        @Test
        @DisplayName("isRead — по умолчанию false")
        void defaultFalse() {
            assertThat(new Notification().isRead()).isFalse();
        }

        @Test
        @DisplayName("setRead(true) — isRead=true")
        void setTrue() {
            Notification n = new Notification(); n.setRead(true);
            assertThat(n.isRead()).isTrue();
        }

        @Test
        @DisplayName("Геттеры/сеттеры Notification работают")
        void gettersSetters() {
            LocalDateTime now = LocalDateTime.now();
            Notification n = new Notification();
            n.setId(1L); n.setUsername("bob"); n.setTitle("Тест");
            n.setBody("Текст"); n.setLink("/profile"); n.setCreatedAt(now);

            assertThat(n.getId()).isEqualTo(1L);
            assertThat(n.getUsername()).isEqualTo("bob");
            assertThat(n.getTitle()).isEqualTo("Тест");
            assertThat(n.getBody()).isEqualTo("Текст");
            assertThat(n.getLink()).isEqualTo("/profile");
            assertThat(n.getCreatedAt()).isEqualTo(now);
        }
    }

    // ─── Review ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Review")
    class ReviewTests {

        @Test
        @DisplayName("Геттеры/сеттеры Review работают")
        void gettersSetters() {
            User user = new User(); user.setId(1L);
            User tgt  = new User(); tgt.setId(2L);
            Book book = new Book(); book.setId(5L);
            LocalDateTime now = LocalDateTime.now();

            Review r = new Review();
            r.setId(1L); r.setUser(user); r.setTargetUser(tgt); r.setBook(book);
            r.setRating(5); r.setComment("Отлично!"); r.setCreatedAt(now); r.setUpdatedAt(now);

            assertThat(r.getId()).isEqualTo(1L);
            assertThat(r.getUser()).isEqualTo(user);
            assertThat(r.getTargetUser()).isEqualTo(tgt);
            assertThat(r.getBook()).isEqualTo(book);
            assertThat(r.getRating()).isEqualTo(5);
            assertThat(r.getComment()).isEqualTo("Отлично!");
            assertThat(r.getCreatedAt()).isEqualTo(now);
            assertThat(r.getUpdatedAt()).isEqualTo(now);
        }
    }

    // ─── Achievement ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Achievement")
    class AchievementTests {

        @Test
        @DisplayName("5 типов AchievementType")
        void fiveTypes() {
            assertThat(Achievement.AchievementType.values()).hasSize(5);
        }

        @Test
        @DisplayName("Геттеры/сеттеры Achievement работают")
        void gettersSetters() {
            Achievement a = new Achievement();
            a.setId(1L); a.setCode("FIRST_BOOK"); a.setTitle("Первопроходец");
            a.setDescription("Добавил первую книгу"); a.setIcon("📚");
            a.setCondition("Добавьте 1 книгу"); a.setConditionValue(1);
            a.setType(Achievement.AchievementType.BOOKS_ADDED);

            assertThat(a.getId()).isEqualTo(1L);
            assertThat(a.getCode()).isEqualTo("FIRST_BOOK");
            assertThat(a.getTitle()).isEqualTo("Первопроходец");
            assertThat(a.getType()).isEqualTo(Achievement.AchievementType.BOOKS_ADDED);
            assertThat(a.getConditionValue()).isEqualTo(1);
        }
    }

    // ─── UserAchievement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("UserAchievement")
    class UserAchievementTests {

        @Test
        @DisplayName("featured — по умолчанию false")
        void defaultFeatured() {
            assertThat(new UserAchievement().isFeatured()).isFalse();
        }

        @Test
        @DisplayName("setFeatured(true) — true")
        void setFeaturedTrue() {
            UserAchievement ua = new UserAchievement(); ua.setFeatured(true);
            assertThat(ua.isFeatured()).isTrue();
        }

        @Test
        @DisplayName("Геттеры/сеттеры UserAchievement работают")
        void gettersSetters() {
            User user = new User(); user.setId(1L);
            Achievement a = new Achievement(); a.setId(2L);
            LocalDateTime now = LocalDateTime.now();

            UserAchievement ua = new UserAchievement();
            ua.setId(1L); ua.setUser(user); ua.setAchievement(a);
            ua.setEarnedAt(now); ua.setFeatured(true);

            assertThat(ua.getId()).isEqualTo(1L);
            assertThat(ua.getUser()).isEqualTo(user);
            assertThat(ua.getAchievement()).isEqualTo(a);
            assertThat(ua.getEarnedAt()).isEqualTo(now);
            assertThat(ua.isFeatured()).isTrue();
        }
    }

    // ─── User.UserRole ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("User.UserRole")
    class UserRoleTests {

        @Test
        @DisplayName("USER, MODERATOR, ADMIN присутствуют")
        void rolesExist() {
            assertThat(User.UserRole.values()).containsExactlyInAnyOrder(
                    User.UserRole.USER, User.UserRole.MODERATOR, User.UserRole.ADMIN);
        }
    }

    // ─── AchievementService.UserStats ─────────────────────────────────────────

    @Nested
    @DisplayName("AchievementService.UserStats")
    class UserStatsTests {

        @Test
        @DisplayName("Все поля устанавливаются и читаются")
        void gettersSetters() {
            AchievementService.UserStats s = new AchievementService.UserStats();
            s.setBooksAdded(5); s.setBooksGiven(3); s.setReviewsWritten(10);
            s.setDaysInSystem(30); s.setComplaintsSent(1); s.setRank("Книгочей");

            assertThat(s.getBooksAdded()).isEqualTo(5);
            assertThat(s.getBooksGiven()).isEqualTo(3);
            assertThat(s.getReviewsWritten()).isEqualTo(10);
            assertThat(s.getDaysInSystem()).isEqualTo(30);
            assertThat(s.getComplaintsSent()).isEqualTo(1);
            assertThat(s.getRank()).isEqualTo("Книгочей");
        }
    }
}