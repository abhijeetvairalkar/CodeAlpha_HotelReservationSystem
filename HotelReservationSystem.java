import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

// Room class
class Room {
    private final int roomNumber;
    private final String category;
    private final double pricePerNight;

    public Room(int roomNumber, String category, double pricePerNight) {
        this.roomNumber = roomNumber;
        this.category = category;
        this.pricePerNight = pricePerNight;
    }

    public int getRoomNumber() { return roomNumber; }
    public String getCategory() { return category; }
    public double getPricePerNight() { return pricePerNight; }

    @Override
    public String toString() {
        return String.format("Room %d (%s) - ₹%.2f/night", roomNumber, category, pricePerNight);
    }
}

// Reservation class
class Reservation {
    private final String reservationId;
    private final String userName;
    private final int roomNumber;
    private final LocalDate checkIn;
    private final LocalDate checkOut;
    private final double totalPrice;

    public Reservation(String reservationId, String userName, int roomNumber, LocalDate checkIn, LocalDate checkOut, double totalPrice) {
        this.reservationId = reservationId;
        this.userName = userName;
        this.roomNumber = roomNumber;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalPrice = totalPrice;
    }

    public String getReservationId() { return reservationId; }
    public String getUserName() { return userName; }
    public int getRoomNumber() { return roomNumber; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public double getTotalPrice() { return totalPrice; }

    @Override
    public String toString() {
        return String.format("Reservation %s: %s | Room %d | %s -> %s | ₹%.2f",
                reservationId, userName, roomNumber, checkIn, checkOut, totalPrice);
    }
}

// Hotel class
class Hotel {
    private final Map<Integer, Room> rooms = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1000);
    private final DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE;

    public void addRoom(Room r) { rooms.put(r.getRoomNumber(), r); }
    public Collection<Room> listRooms() { return rooms.values(); }

    // Search available rooms by category and date
    public List<Room> searchAvailable(String category, LocalDate checkIn, LocalDate checkOut) {
        List<Room> result = new ArrayList<>();
        for (Room r : rooms.values()) {
            if (!r.getCategory().equalsIgnoreCase(category)) continue;
            if (isRoomAvailable(r.getRoomNumber(), checkIn, checkOut)) result.add(r);
        }
        return result;
    }

    private boolean isRoomAvailable(int roomNumber, LocalDate checkIn, LocalDate checkOut) {
        for (Reservation res : reservations.values()) {
            if (res.getRoomNumber() != roomNumber) continue;
            // check date overlap
            if (checkIn.isBefore(res.getCheckOut()) && res.getCheckIn().isBefore(checkOut)) {
                return false;
            }
        }
        return true;
    }

    public Reservation makeReservation(String userName, int roomNumber, LocalDate checkIn, LocalDate checkOut) {
        Room room = rooms.get(roomNumber);
        if (room == null) throw new IllegalArgumentException("Room not found: " + roomNumber);
        if (!isRoomAvailable(roomNumber, checkIn, checkOut)) return null;
        long nights = checkOut.toEpochDay() - checkIn.toEpochDay();
        if (nights <= 0) throw new IllegalArgumentException("Check-out must be after check-in");
        double total = nights * room.getPricePerNight();
        String id = "R" + idCounter.getAndIncrement();
        Reservation res = new Reservation(id, userName, roomNumber, checkIn, checkOut, total);
        reservations.put(id, res);
        return res;
    }

    public boolean cancelReservation(String reservationId) {
        return reservations.remove(reservationId) != null;
    }

    public Reservation findReservation(String reservationId) {
        return reservations.get(reservationId);
    }

    public Collection<Reservation> listReservations() { return reservations.values(); }

    // File I/O methods
    public void loadRooms(Path p) throws IOException {
        if (!Files.exists(p)) return;
        List<String> lines = Files.readAllLines(p);
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",");
            int rn = Integer.parseInt(parts[0]);
            String cat = parts[1];
            double price = Double.parseDouble(parts[2]);
            addRoom(new Room(rn, cat, price));
        }
    }

    public void saveRooms(Path p) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Room r : rooms.values())
            lines.add(String.join(",", String.valueOf(r.getRoomNumber()), r.getCategory(), String.valueOf(r.getPricePerNight())));
        Files.write(p, lines);
    }

    public void loadReservations(Path p) throws IOException {
        if (!Files.exists(p)) return;
        List<String> lines = Files.readAllLines(p);
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",");
            String id = parts[0];
            String user = parts[1];
            int roomNum = Integer.parseInt(parts[2]);
            LocalDate ci = LocalDate.parse(parts[3], dtf);
            LocalDate co = LocalDate.parse(parts[4], dtf);
            double total = Double.parseDouble(parts[5]);
            reservations.put(id, new Reservation(id, user, roomNum, ci, co, total));
            try {
                int numeric = Integer.parseInt(id.replaceAll("[^0-9]", ""));
                idCounter.updateAndGet(x -> Math.max(x, numeric + 1));
            } catch (Exception ignored) {}
        }
    }

    public void saveReservations(Path p) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Reservation r : reservations.values()) {
            lines.add(String.join(",", r.getReservationId(), r.getUserName(), String.valueOf(r.getRoomNumber()),
                    r.getCheckIn().format(dtf), r.getCheckOut().format(dtf), String.valueOf(r.getTotalPrice())));
        }
        Files.write(p, lines);
    }
}

// Main class
public class HotelReservationSystem {
    private static final Scanner sc = new Scanner(System.in);
    private static final Hotel hotel = new Hotel();
    private static final Path ROOMS_FILE = Paths.get("rooms.csv");
    private static final Path RES_FILE = Paths.get("reservations.csv");
    private static final DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE;

    public static void main(String[] args) {
        try { hotel.loadRooms(ROOMS_FILE); } catch (Exception e) { System.out.println("Could not load rooms: " + e.getMessage()); }
        try { hotel.loadReservations(RES_FILE); } catch (Exception e) { System.out.println("Could not load reservations: " + e.getMessage()); }

        if (hotel.listRooms().isEmpty()) createSampleRooms();

        loop: while (true) {
            printMenu();
            String cmd = sc.nextLine().trim().toUpperCase();
            switch (cmd) {
                case "1": doSearch(); break;
                case "2": doBook(); break;
                case "3": doCancel(); break;
                case "4": doView(); break;
                case "5": listAllRooms(); break;
                case "6": listAllReservations(); break;
                case "0":
                    try { hotel.saveRooms(ROOMS_FILE); hotel.saveReservations(RES_FILE); System.out.println("Saved."); } catch (Exception e) { System.out.println("Save failed: " + e.getMessage()); }
                    break loop;
                default: System.out.println("Unknown option");
            }
        }
        System.out.println("Goodbye");
    }

    private static void printMenu() {
        System.out.println("\n--- Hotel Reservation System ---");
        System.out.println("1) Search available rooms");
        System.out.println("2) Book a room");
        System.out.println("3) Cancel reservation");
        System.out.println("4) View reservation");
        System.out.println("5) List all rooms");
        System.out.println("6) List all reservations");
        System.out.println("0) Save & Exit");
        System.out.print("Choose: ");
    }

    private static void createSampleRooms() {
        hotel.addRoom(new Room(101, "Standard", 2500));
        hotel.addRoom(new Room(102, "Standard", 2500));
        hotel.addRoom(new Room(201, "Deluxe", 4000));
        hotel.addRoom(new Room(202, "Deluxe", 4500));
        hotel.addRoom(new Room(301, "Suite", 8000));
    }

    private static void doSearch() {
        System.out.print("Enter category (Standard/Deluxe/Suite): ");
        String cat = sc.nextLine().trim();
        LocalDate ci = readDate("Check-in date (YYYY-MM-DD): ");
        LocalDate co = readDate("Check-out date (YYYY-MM-DD): ");
        List<Room> available = hotel.searchAvailable(cat, ci, co);
        if (available.isEmpty()) System.out.println("No rooms available for that period.");
        else { System.out.println("Available rooms:"); for (Room r : available) System.out.println("  " + r); }
    }

    private static void doBook() {
        try {
            System.out.print("Your name: "); String name = sc.nextLine().trim();
            System.out.print("Room number: "); int rn = Integer.parseInt(sc.nextLine().trim());
            LocalDate ci = readDate("Check-in date (YYYY-MM-DD): ");
            LocalDate co = readDate("Check-out date (YYYY-MM-DD): ");
            Reservation res = hotel.makeReservation(name, rn, ci, co);
            if (res == null) { System.out.println("Room not available for those dates."); return; }
            System.out.printf("Total price: ₹%.2f. Proceed to payment? (y/n): ", res.getTotalPrice());
            String ok = sc.nextLine().trim().toLowerCase();
            if (!ok.equals("y")) { System.out.println("Booking cancelled by user."); return; }
            System.out.println("Processing payment..."); try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            System.out.println("Payment successful.");
            System.out.println("Booking confirmed: " + res.getReservationId());
        } catch (NumberFormatException nfe) { System.out.println("Invalid number input."); }
        catch (IllegalArgumentException iae) { System.out.println("Error: " + iae.getMessage()); }
    }

    private static void doCancel() {
        System.out.print("Enter reservation ID to cancel: ");
        String id = sc.nextLine().trim();
        Reservation r = hotel.findReservation(id);
        if (r == null) { System.out.println("Reservation not found."); return; }
        System.out.print("Are you sure you want to cancel reservation " + id + "? (y/n): ");
        String y = sc.nextLine().trim().toLowerCase();
        if (y.equals("y")) { boolean ok = hotel.cancelReservation(id); System.out.println(ok ? "Cancelled." : "Cancel failed."); }
        else System.out.println("Cancellation aborted.");
    }

    private static void doView() {
        System.out.print("Enter reservation ID: ");
        String id = sc.nextLine().trim();
        Reservation r = hotel.findReservation(id);
        if (r == null) System.out.println("Not found.");
        else System.out.println(r);
    }

    private static void listAllRooms() {
        System.out.println("All rooms:");
        for (Room r : hotel.listRooms()) System.out.println("  " + r);
    }

    private static void listAllReservations() {
        System.out.println("Reservations:");
        for (Reservation r : hotel.listReservations()) System.out.println("  " + r);
    }

    private static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try { return LocalDate.parse(s, dtf); }
            catch (Exception e) { System.out.println("Bad date format, please use YYYY-MM-DD."); }
        }
    }
}
