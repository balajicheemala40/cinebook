package com.demo.movieticketbooking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping(value={"/api"})
public class BookingController {
    @Value(value="${tmdb.api.key}")
    private String tmdbKey;
    private static final String DEMO_OTP = "123456";
    private static final String IMG = "https://image.tmdb.org/t/p/";
    private final RestClient http = RestClient.create("https://api.themoviedb.org/3");
    private static final Map<String, String> LANGS = new LinkedHashMap<>();
    private static final Map<Integer, String> GENRES;
    private final List<Theatre> allTheatres = List.of(new Theatre(1, "PVR Cinemas", "Vijayawada", List.of("10:00 AM", "1:15 PM", "4:30 PM", "7:45 PM"), 4.3, 0), new Theatre(2, "INOX", "Guntur", List.of("11:30 AM", "3:00 PM", "6:30 PM", "9:45 PM"), 4.1, 0), new Theatre(3, "Cinepolis", "Mangalagiri", List.of("9:30 AM", "12:45 PM", "6:00 PM", "9:00 PM"), 4.5, 0), new Theatre(4, "Asian Cinemas", "Tenali", List.of("10:30 AM", "2:00 PM", "8:15 PM"), 4.2, 0));
    private List<Movie> cache = List.of();
    private long cachedAt = 0L;
    private final Map<String, Set<String>> booked = new ConcurrentHashMap<>();

    static {
        LANGS.put("te", "Telugu");
        LANGS.put("ta", "Tamil");
        LANGS.put("hi", "Hindi");
        LANGS.put("ml", "Malayalam");
        LANGS.put("kn", "Kannada");
        LANGS.put("en", "English");
        GENRES = Map.ofEntries(Map.entry(28, "Action"), Map.entry(12, "Adventure"), Map.entry(16, "Animation"), Map.entry(35, "Comedy"), Map.entry(80, "Crime"), Map.entry(99, "Documentary"), Map.entry(18, "Drama"), Map.entry(10751, "Family"), Map.entry(14, "Fantasy"), Map.entry(36, "History"), Map.entry(27, "Horror"), Map.entry(10402, "Music"), Map.entry(9648, "Mystery"), Map.entry(10749, "Romance"), Map.entry(878, "Sci-Fi"), Map.entry(53, "Thriller"), Map.entry(10752, "War"), Map.entry(37, "Western"));
    }

    @PostMapping(value={"/send-otp"})
    public Map<String, String> sendOtp(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("\\d{10}")) {
            return Map.of("status", "error", "message", "Enter a valid 10-digit number");
        }
        return Map.of("status", "ok", "message", "Demo OTP sent (use 123456)");
    }

    @PostMapping(value={"/verify-otp"})
    public Map<String, String> verifyOtp(@RequestBody Map<String, String> body) {
        if (DEMO_OTP.equals(body.get("otp"))) {
            return Map.of("status", "ok", "token", UUID.randomUUID().toString());
        }
        return Map.of("status", "error", "message", "Invalid OTP");
    }

    @GetMapping(value={"/movies"})
    public synchronized List<Movie> getMovies() {
        if (this.cache.isEmpty() || System.currentTimeMillis() - this.cachedAt > 1800000L) {
            ArrayList<Movie> all = new ArrayList<>();
            LANGS.forEach((code, name) -> all.addAll(fetchLanguage(code, name)));
            if (!all.isEmpty()) {
                this.cache = all;
                this.cachedAt = System.currentTimeMillis();
            }
        }
        return this.cache;
    }

    private List<Movie> fetchLanguage(String code, String langName) {
        ArrayList<Movie> out = new ArrayList<>();
        try {
            LocalDate today = LocalDate.now();
            Map<String, Object> response = http.get()
                    .uri(uri -> uri.path("/discover/movie")
                            .queryParam("api_key", tmdbKey)
                            .queryParam("region", "IN")
                            .queryParam("with_release_type", "2|3")
                            .queryParam("release_date.gte", today.minusDays(45))
                            .queryParam("release_date.lte", today.plusDays(7))
                            .queryParam("with_original_language", code)
                            .queryParam("sort_by", "popularity.desc")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null || !(response.get("results") instanceof List<?> results)) {
                return out;
            }
            for (Object result : results) {
                if (out.size() == 12) {
                    break;
                }
                if (!(result instanceof Map<?, ?> movie) || !(movie.get("poster_path") instanceof String poster)) {
                    continue;
                }
                String backdrop = (String) movie.get("backdrop_path");
                List<?> genreIds = (List<?>) movie.get("genre_ids");
                String genre = genreIds.stream().limit(2)
                        .map(id -> GENRES.getOrDefault(((Number) id).intValue(), ""))
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.joining(" / "));
                double rating = Math.round(((Number) movie.get("vote_average")).doubleValue() * 10) / 10.0;
                out.add(new Movie(((Number) movie.get("id")).intValue(), (String) movie.get("title"),
                        langName, genre, IMG + "w342" + poster, backdrop == null ? "" : IMG + "w780" + backdrop,
                        rating, (String) movie.get("release_date"), (String) movie.get("overview")));
            }
        }
        catch (Exception e) {
            System.out.println("TMDB error for " + code + ": " + e.getMessage());
        }
        return out;
    }

    @GetMapping(value={"/movies/{id}/theatres"})
    public List<Theatre> getTheatres(@PathVariable int id) {
        return this.allTheatres;
    }

    @GetMapping(value={"/seats"})
    public Set<String> bookedSeats(@RequestParam String show) {
        return this.booked.getOrDefault(show, Set.of());
    }

    @PostMapping(value={"/book"})
    public Map<String, Object> book(@RequestBody Map<String, Object> body) {
        String show = (String)body.get("show");
        List<?> requestedSeats = (List<?>) body.get("seats");
        List<String> seats = requestedSeats.stream().map(String.class::cast).toList();
        Set<String> taken = this.booked.computeIfAbsent(show, k -> ConcurrentHashMap.newKeySet());
        for (String s : seats) {
            if (!taken.contains(s)) continue;
            return Map.of("status", "error", "message", "Seat " + s + " was just booked by someone else");
        }
        taken.addAll(seats);
        return Map.of("status", "ok", "bookingId", "CB" + (100000 + new Random().nextInt(900000)));
    }

    public record Movie(int id, String title, String language, String genre, String poster, String backdrop, double rating, String releaseDate, String overview) {
    }

    public record Theatre(int id, String name, String location, List<String> showTimes, double rating, int reviewCount) {
    }
}
