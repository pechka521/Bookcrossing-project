package com.bookcrossing.service;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AchievementService {

    private final AchievementRepository       achievementRepository;
    private final UserAchievementRepository   userAchievementRepository;
    private final BookRepository              bookRepository;
    private final ReviewRepository            reviewRepository;
    private final ComplaintRepository         complaintRepository;
    private final NotificationService         notificationService;

    public AchievementService(AchievementRepository achievementRepository,
                              UserAchievementRepository userAchievementRepository,
                              BookRepository bookRepository,
                              ReviewRepository reviewRepository,
                              ComplaintRepository complaintRepository,
                              NotificationService notificationService) {
        this.achievementRepository     = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.bookRepository            = bookRepository;
        this.reviewRepository          = reviewRepository;
        this.complaintRepository       = complaintRepository;
        this.notificationService       = notificationService;
    }

    @PostConstruct
    @Transactional
    public void initAchievements() {
        seedIfAbsent("FIRST_BOOK",    "Первопроходец",      "Добавил первую книгу в каталог",            "📚",  "Добавьте 1 книгу в каталог",                      1,   Achievement.AchievementType.BOOKS_ADDED);
        seedIfAbsent("BOOKWORM_5",    "Книгочей",           "Поделился пятью книгами",                   "📖",  "Добавьте 5 книг в каталог",                       5,   Achievement.AchievementType.BOOKS_ADDED);
        seedIfAbsent("LIBRARIAN_10",  "Библиотекарь",       "Добавил 10 книг — настоящий библиотекарь!", "🏛️", "Добавьте 10 книг в каталог",                      10,  Achievement.AchievementType.BOOKS_ADDED);
        seedIfAbsent("LEGEND_25",     "Легенда полок",      "25 книг — живая легенда библиотеки",        "🌟",  "Добавьте 25 книг в каталог",                      25,  Achievement.AchievementType.BOOKS_ADDED);
        seedIfAbsent("FIRST_SHARE",   "Первая передача",    "Передал первую книгу читателю",             "🤝",  "Отметьте 1 книгу как переданную (статус «Занята»)", 1,  Achievement.AchievementType.BOOKS_GIVEN);
        seedIfAbsent("GENEROUS_5",    "Щедрый читатель",    "Передал 5 книг другим",                     "🎁",  "Передайте 5 книг (статус «Занята»)",               5,   Achievement.AchievementType.BOOKS_GIVEN);
        seedIfAbsent("PATRON_10",     "Меценат литературы", "Десять переданных книг — это подвиг!",      "💎",  "Передайте 10 книг (статус «Занята»)",              10,  Achievement.AchievementType.BOOKS_GIVEN);
        seedIfAbsent("FIRST_REVIEW",  "Первый критик",      "Написал первый отзыв на книгу",             "⭐",  "Напишите 1 отзыв",                                 1,   Achievement.AchievementType.REVIEWS_WRITTEN);
        seedIfAbsent("REVIEWER_5",    "Литературный критик","Оставил 5 развёрнутых отзывов",             "✍️", "Напишите 5 отзывов",                               5,   Achievement.AchievementType.REVIEWS_WRITTEN);
        seedIfAbsent("VETERAN_30",    "Старожил",           "30 дней в нашем сообществе",                "🗓️", "Проведите в системе 30 дней",                      30,  Achievement.AchievementType.DAYS_IN_SYSTEM);
        seedIfAbsent("SENIOR_90",     "Ветеран",            "Три месяца активного участия",              "📅",  "Проведите в системе 90 дней",                      90,  Achievement.AchievementType.DAYS_IN_SYSTEM);
        seedIfAbsent("ACTIVIST_365",  "Хранитель знаний",   "Целый год с BookCrossing!",                 "🔥",  "Проведите в системе 365 дней",                     365, Achievement.AchievementType.DAYS_IN_SYSTEM);
    }

    private void seedIfAbsent(String code, String title, String description,
                              String icon, String condition, int value,
                              Achievement.AchievementType type) {
        if (achievementRepository.findByCode(code).isEmpty()) {
            Achievement a = new Achievement();
            a.setCode(code); a.setTitle(title); a.setDescription(description);
            a.setIcon(icon); a.setCondition(condition); a.setConditionValue(value);
            a.setType(type);
            achievementRepository.save(a);
        }
    }

    @Transactional(readOnly = true)
    public UserStats calculateStats(User user) {
        UserStats stats = new UserStats();

        List<Book> books = bookRepository.findByOwner(user);
        stats.setBooksAdded((long) books.size());

        long given = books.stream()
                .filter(b -> b.getStatus() == Book.BookStatus.BUSY)
                .count();
        stats.setBooksGiven(given);

        stats.setReviewsWritten(reviewRepository.countByUser(user));

        long days = 0;
        if (user.getRegisteredAt() != null) {
            days = ChronoUnit.DAYS.between(user.getRegisteredAt().toLocalDate(), LocalDate.now());
        }
        stats.setDaysInSystem(days);

        stats.setComplaintsSent(complaintRepository.countByAuthor(user));
        stats.setRank(calculateRank(stats));
        return stats;
    }

    private String calculateRank(UserStats stats) {
        long total = stats.getBooksAdded() + stats.getBooksGiven() * 2;
        if (total >= 50) return "🏆 Хранитель знаний";
        if (total >= 25) return "💎 Меценат литературы";
        if (total >= 10) return "🌟 Активный читатель";
        if (total >= 5)  return "📖 Книгочей";
        if (total >= 1)  return "📚 Начинающий";
        return "👤 Новичок";
    }

    @Async
    @Transactional
    public void checkAndAward(User user) {
        UserStats stats = calculateStats(user);
        Set<String> earned = userAchievementRepository.findEarnedCodesByUser(user);
        List<Achievement> all = achievementRepository.findAllByOrderById();
        for (Achievement a : all) {
            if (earned.contains(a.getCode())) continue;
            boolean qualifies = switch (a.getType()) {
                case BOOKS_ADDED     -> stats.getBooksAdded()     >= a.getConditionValue();
                case BOOKS_GIVEN     -> stats.getBooksGiven()     >= a.getConditionValue();
                case REVIEWS_WRITTEN -> stats.getReviewsWritten() >= a.getConditionValue();
                case DAYS_IN_SYSTEM  -> stats.getDaysInSystem()   >= a.getConditionValue();
                case COMPLAINTS_SENT -> stats.getComplaintsSent() >= a.getConditionValue();
            };
            if (qualifies) award(user, a);
        }
    }

    private void award(User user, Achievement achievement) {
        UserAchievement ua = new UserAchievement();
        ua.setUser(user);
        ua.setAchievement(achievement);
        ua.setEarnedAt(LocalDateTime.now());
        userAchievementRepository.save(ua);
        notificationService.sendNotification(
                user.getUsername(),
                "🏅 Новое достижение!",
                achievement.getIcon() + " «" + achievement.getTitle() + "» — " + achievement.getDescription(),
                "/profile"
        );
    }

    @Transactional(readOnly = true)
    public List<AchievementDto> getUserAchievements(User user) {
        List<Achievement> all = achievementRepository.findAllByOrderById();
        List<UserAchievement> earnedList = userAchievementRepository.findByUserOrderByEarnedAtDesc(user);

        Map<String, UserAchievement> uaMap = new HashMap<>();
        for (UserAchievement ua : earnedList) {
            uaMap.put(ua.getAchievement().getCode(), ua);
        }

        List<AchievementDto> result = new ArrayList<>();
        for (Achievement a : all) {
            UserAchievement ua = uaMap.get(a.getCode());
            boolean isEarned = ua != null;
            result.add(new AchievementDto(
                    a.getId(), a.getCode(), a.getTitle(), a.getDescription(),
                    a.getIcon(), a.getCondition(), a.getConditionValue(),
                    isEarned,
                    isEarned ? ua.getEarnedAt() : null,
                    isEarned && ua.isFeatured()
            ));
        }
        return result;
    }

    public static class UserStats {
        private long booksAdded, booksGiven, reviewsWritten, daysInSystem, complaintsSent;
        private String rank;
        public long getBooksAdded()           { return booksAdded; }
        public void setBooksAdded(long v)     { this.booksAdded = v; }
        public long getBooksGiven()           { return booksGiven; }
        public void setBooksGiven(long v)     { this.booksGiven = v; }
        public long getReviewsWritten()       { return reviewsWritten; }
        public void setReviewsWritten(long v) { this.reviewsWritten = v; }
        public long getDaysInSystem()         { return daysInSystem; }
        public void setDaysInSystem(long v)   { this.daysInSystem = v; }
        public long getComplaintsSent()       { return complaintsSent; }
        public void setComplaintsSent(long v) { this.complaintsSent = v; }
        public String getRank()               { return rank; }
        public void setRank(String r)         { this.rank = r; }
    }

    public record AchievementDto(
            Long id, String code, String title, String description,
            String icon, String condition, Integer conditionValue,
            boolean earned, LocalDateTime earnedAt, boolean featured
    ) {}
}