package com.dehui.property.modules.finance.service;

import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FinanceMetricsService {
    private final RoomRepository roomRepository;
    private final ContractRepository contractRepository;

    public RoomRentalStats overallRoomRentalStats() {
        LocalDate today = LocalDate.now();
        long rentable = defaultCount(roomRepository.countRentableRooms());
        long rented = defaultCount(contractRepository.countActiveLeasedRoomIds(today));
        return RoomRentalStats.of(rentable, rented);
    }

    public RoomRentalStats buildingRoomRentalStats(Long buildingId) {
        LocalDate today = LocalDate.now();
        long rentable = defaultCount(roomRepository.countRentableRoomsByBuildingId(buildingId));
        long rented = defaultCount(contractRepository.countActiveLeasedRoomIdsByBuildingId(today, buildingId));
        return RoomRentalStats.of(rentable, rented);
    }

    private long defaultCount(Long value) {
        return value == null ? 0L : value;
    }

    public record RoomRentalStats(long rentableRoomCount, long rentedRoomCount) {
        public static RoomRentalStats of(long rentableRoomCount, long rentedRoomCount) {
            long normalizedRentable = Math.max(rentableRoomCount, 0);
            long normalizedRented = Math.min(Math.max(rentedRoomCount, 0), normalizedRentable);
            return new RoomRentalStats(normalizedRentable, normalizedRented);
        }

        public long availableRoomCount() {
            return Math.max(rentableRoomCount - rentedRoomCount, 0);
        }

        public double rentalRate() {
            if (rentableRoomCount <= 0) {
                return 0.0;
            }
            return Math.round(rentedRoomCount * 10000.0 / rentableRoomCount) / 100.0;
        }

        public double vacancyRate() {
            if (rentableRoomCount <= 0) {
                return 0.0;
            }
            return Math.round(availableRoomCount() * 10000.0 / rentableRoomCount) / 100.0;
        }
    }
}
