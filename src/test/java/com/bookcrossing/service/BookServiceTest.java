package com.bookcrossing.service;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookService")
class BookServiceTest {

    @Mock BookRepository bookRepository;
    @Mock ReviewRepository reviewRepository;
    @InjectMocks BookService bookService;

    private User owner;
    private Book book;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("testuser");

        book = new Book();
        book.setId(10L);
        book.setTitle("Тестовая книга");
        book.setOwner(owner);
        book.setStatus(Book.BookStatus.FREE);
    }

    // ─── searchBooks ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchBooks")
    class SearchBooks {

        @Test
        @DisplayName("Без фильтров — возвращает все книги")
        void noFilters_returnsAll() {
            when(bookRepository.findAll()).thenReturn(List.of(book));
            List<Book> result = bookService.searchBooks(null, null);
            assertThat(result).hasSize(1);
            verify(bookRepository).findAll();
        }

        @Test
        @DisplayName("Пустые строки — тоже возвращает все")
        void blankFilters_returnsAll() {
            when(bookRepository.findAll()).thenReturn(List.of(book));
            List<Book> result = bookService.searchBooks("  ", "");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Только запрос — searchByQuery")
        void onlyQuery_callsSearchByQuery() {
            when(bookRepository.searchByQuery("war")).thenReturn(List.of(book));
            List<Book> result = bookService.searchBooks("war", null);
            assertThat(result).containsExactly(book);
            verify(bookRepository).searchByQuery("war");
        }

        @Test
        @DisplayName("Только жанр — searchByGenre")
        void onlyGenre_callsSearchByGenre() {
            when(bookRepository.searchByGenre("FANTASY")).thenReturn(List.of(book));
            List<Book> result = bookService.searchBooks(null, "FANTASY");
            assertThat(result).containsExactly(book);
            verify(bookRepository).searchByGenre("FANTASY");
        }

        @Test
        @DisplayName("Запрос + жанр — searchByQueryAndGenre")
        void queryAndGenre_callsSearchByQueryAndGenre() {
            when(bookRepository.searchByQueryAndGenre("war", "HISTORY")).thenReturn(List.of(book));
            List<Book> result = bookService.searchBooks("war", "HISTORY");
            assertThat(result).containsExactly(book);
            verify(bookRepository).searchByQueryAndGenre("war", "HISTORY");
        }
    }

    // ─── getMyBooks ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyBooks")
    class GetMyBooks {

        @Test
        @DisplayName("Без поиска — все книги владельца")
        void noQuery_findByOwner() {
            when(bookRepository.findByOwner(owner)).thenReturn(List.of(book));
            List<Book> result = bookService.getMyBooks(owner, null, null);
            assertThat(result).containsExactly(book);
            verify(bookRepository).findByOwner(owner);
        }

        @Test
        @DisplayName("С поиском — searchByOwnerAndQuery")
        void withQuery_callsOwnerSearch() {
            when(bookRepository.searchByOwnerAndQuery(owner, "тест")).thenReturn(List.of(book));
            List<Book> result = bookService.getMyBooks(owner, "тест", null);
            assertThat(result).containsExactly(book);
            verify(bookRepository).searchByOwnerAndQuery(owner, "тест");
        }
    }

    // ─── saveBook ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveBook")
    class SaveBook {

        @Test
        @DisplayName("Без файла обложки — сохраняет без imageUrl")
        void noCoverFile_savesBookAsIs() {
            when(bookRepository.save(book)).thenReturn(book);
            Book result = bookService.saveBook(book, owner, null);
            assertThat(result.getOwner()).isEqualTo(owner);
            verify(bookRepository).save(book);
        }

        @Test
        @DisplayName("С файлом обложки — сохраняет base64 imageUrl")
        void withCoverFile_setsBase64ImageUrl() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getBytes()).thenReturn("imgdata".getBytes());
            when(file.getContentType()).thenReturn("image/jpeg");
            when(bookRepository.save(book)).thenReturn(book);

            Book result = bookService.saveBook(book, owner, file);

            assertThat(result.getImageUrl()).startsWith("data:image/jpeg;base64,");
            verify(bookRepository).save(book);
        }

        @Test
        @DisplayName("Пустой файл — imageUrl не устанавливается")
        void emptyFile_imageUrlNotSet() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);
            when(bookRepository.save(book)).thenReturn(book);

            bookService.saveBook(book, owner, file);

            assertThat(book.getImageUrl()).isNull();
        }

        @Test
        @DisplayName("IOException при чтении файла — бросает RuntimeException")
        void fileReadError_throwsRuntime() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getBytes()).thenThrow(new IOException("disk error"));

            assertThatThrownBy(() -> bookService.saveBook(book, owner, file))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Ошибка загрузки обложки");
        }
    }

    // ─── toggleStatus ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleStatus")
    class ToggleStatus {

        @Test
        @DisplayName("Книга не найдена — возвращает null")
        void bookNotFound_returnsNull() {
            when(bookRepository.findById(99L)).thenReturn(Optional.empty());
            assertThat(bookService.toggleStatus(99L, owner)).isNull();
        }

        @Test
        @DisplayName("Чужая книга — возвращает null")
        void notOwner_returnsNull() {
            User other = new User();
            other.setId(2L);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            assertThat(bookService.toggleStatus(10L, other)).isNull();
        }

        @Test
        @DisplayName("FREE → BUSY")
        void freeToggledToBusy() {
            book.setStatus(Book.BookStatus.FREE);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookRepository.save(book)).thenReturn(book);

            Book result = bookService.toggleStatus(10L, owner);

            assertThat(result.getStatus()).isEqualTo(Book.BookStatus.BUSY);
        }

        @Test
        @DisplayName("BUSY → FREE")
        void busyToggledToFree() {
            book.setStatus(Book.BookStatus.BUSY);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookRepository.save(book)).thenReturn(book);

            Book result = bookService.toggleStatus(10L, owner);

            assertThat(result.getStatus()).isEqualTo(Book.BookStatus.FREE);
        }
    }

    // ─── deleteBook ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteBook")
    class DeleteBook {

        @Test
        @DisplayName("Книга не найдена — возвращает false")
        void bookNotFound_returnsFalse() {
            when(bookRepository.findById(99L)).thenReturn(Optional.empty());
            assertThat(bookService.deleteBook(99L, owner)).isFalse();
        }

        @Test
        @DisplayName("Не владелец — возвращает false")
        void notOwner_returnsFalse() {
            User other = new User();
            other.setId(2L);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            assertThat(bookService.deleteBook(10L, other)).isFalse();
        }

        @Test
        @DisplayName("Владелец — удаляет отзывы и книгу, возвращает true")
        void owner_deletesReviewsAndBook() {
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            boolean result = bookService.deleteBook(10L, owner);

            assertThat(result).isTrue();
            verify(reviewRepository).deleteByBookId(10L);
            verify(bookRepository).delete(book);
        }
    }
}