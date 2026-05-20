package com.dehui.property.modules.meeting.repository;

import com.dehui.property.modules.meeting.entity.MeetingRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {
    Optional<MeetingRoom> findFirstByRoomName(String roomName);

    List<MeetingRoom> findByStatusOrderByRoomNameAsc(String status);
}
