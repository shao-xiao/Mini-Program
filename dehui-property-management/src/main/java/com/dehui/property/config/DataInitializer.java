package com.dehui.property.config;

import com.dehui.property.modules.building.entity.Building;
import com.dehui.property.modules.building.entity.Floor;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.BuildingRepository;
import com.dehui.property.modules.building.repository.FloorRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.energy.entity.EnergyMeter;
import com.dehui.property.modules.energy.entity.EnergyRateRule;
import com.dehui.property.modules.energy.entity.EnergyReading;
import com.dehui.property.modules.energy.repository.EnergyMeterRepository;
import com.dehui.property.modules.energy.repository.EnergyRateRuleRepository;
import com.dehui.property.modules.energy.repository.EnergyReadingRepository;
import com.dehui.property.modules.meeting.entity.MeetingRoom;
import com.dehui.property.modules.meeting.entity.MeetingBooking;
import com.dehui.property.modules.meeting.repository.MeetingBookingRepository;
import com.dehui.property.modules.meeting.repository.MeetingRoomRepository;
import com.dehui.property.modules.system.entity.SysRole;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.entity.UserRole;
import com.dehui.property.modules.system.repository.SysRoleRepository;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.system.repository.UserRoleRepository;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EnergyRateRuleRepository energyRateRuleRepository;
    private final EnergyMeterRepository energyMeterRepository;
    private final EnergyReadingRepository energyReadingRepository;
    private final TenantRepository tenantRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final MeetingBookingRepository meetingBookingRepository;
    private final BillRepository billRepository;
    private final Environment environment;

    @PostConstruct
    public void init() {
        initSystemUserAndRoles();
        initBuildingFloorsAndRooms();
        initEnergyRateRules();
        initDevFixtures();
        initEnergyFixtures();
        log.info("数据初始化完成");
    }

    private void initSystemUserAndRoles() {
        List<String> roleCodes = List.of("ADMIN", "MANAGER", "STAFF", "SECURITY", "CLEANER", "FINANCE");

        for (String code : roleCodes) {
            boolean exists = sysRoleRepository.findAll()
                    .stream()
                    .anyMatch(r -> code.equals(r.getRoleCode()));

            if (!exists) {
                SysRole role = new SysRole();
                role.setRoleCode(code);
                role.setRoleName(code);
                role.setStatus("ACTIVE");
                sysRoleRepository.save(role);
                log.info("初始化角色: {}", code);
            }
        }

        SysUser admin = sysUserRepository.findByUsername("admin").orElseGet(() -> {
            SysUser user = new SysUser();
            user.setUsername("admin");
            user.setPassword("123456");
            user.setRealName("系统管理员");
            user.setPhone("13800000000");
            user.setStatus("ACTIVE");
            return sysUserRepository.save(user);
        });

        SysRole adminRole = sysRoleRepository.findAll()
                .stream()
                .filter(r -> "ADMIN".equals(r.getRoleCode()))
                .findFirst()
                .orElse(null);

        if (adminRole != null) {
            boolean assigned = userRoleRepository.findByUserId(admin.getId())
                    .stream()
                    .anyMatch(ur -> adminRole.getId().equals(ur.getRoleId()));

            if (!assigned) {
                UserRole userRole = new UserRole();
                userRole.setUserId(admin.getId());
                userRole.setRoleId(adminRole.getId());
                userRoleRepository.save(userRole);
                log.info("为 admin 分配 ADMIN 角色");
            }
        }
    }

    private void initBuildingFloorsAndRooms() {
        Building building = buildingRepository.findById(1L).orElseGet(() -> {
            Building b = new Building();
            b.setBuildingName("德汇创新中心");
            return buildingRepository.save(b);
        });

        List<String> names = List.of(
                "B2", "B1", "一楼", "二楼", "三楼",
                "四楼", "五楼", "六楼", "七楼", "八楼", "九楼"
        );

        List<Integer> numbers = List.of(
                -2, -1, 1, 2, 3, 4, 5, 6, 7, 8, 9
        );

        List<Floor> existingFloors = floorRepository.findAll();

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Integer number = numbers.get(i);

            boolean exists = existingFloors.stream()
                    .anyMatch(f -> name.equals(f.getFloorName()));

            if (!exists) {
                Floor floor = new Floor();
                floor.setFloorName(name);
                floor.setFloorNumber(number);
                floor.setBuilding(building);
                floorRepository.save(floor);
                log.info("初始化楼层: {}", name);
            }
        }

        if (roomRepository.count() == 0) {
            List<Floor> floors = floorRepository.findAll();

            for (Floor floor : floors) {
                for (int i = 1; i <= 5; i++) {
                    Room room = new Room();
                    room.setRoomNumber(floor.getFloorName() + "0" + i);
                    room.setArea(100.0);
                    room.setRoomType("OFFICE");
                    room.setStatus("AVAILABLE");
                    room.setBuilding(building);
                    room.setFloor(floor);
                    roomRepository.save(room);
                }
            }

            log.info("初始化房间完成");
        }

        roomRepository.findAll().stream()
                .filter(room -> room.getBuilding() == null && room.getFloor() != null)
                .forEach(room -> {
                    floorRepository.findWithBuildingById(room.getFloor().getId()).ifPresent(floor -> {
                        room.setBuilding(floor.getBuilding());
                        roomRepository.save(room);
                        log.info("回填房间楼宇关系: roomId={}, buildingId={}", room.getId(), room.getBuilding().getId());
                    });
                });
    }

    private void initEnergyRateRules() {
        createEnergyRateRuleIfMissing("ELECTRICITY", new BigDecimal("1.00"), "默认电费单价");
        createEnergyRateRuleIfMissing("WATER", new BigDecimal("4.00"), "默认水费单价");
        createEnergyRateRuleIfMissing("GAS", new BigDecimal("3.00"), "默认煤气单价");
    }

    private void createEnergyRateRuleIfMissing(String energyType, BigDecimal unitPrice, String remark) {
        List<EnergyRateRule> rules = energyRateRuleRepository.findByEnergyTypeOrderByUpdatedTimeDesc(energyType);
        EnergyRateRule defaultRule = rules
                .stream()
                .filter(rule -> remark.equals(rule.getRemark()))
                .findFirst()
                .orElse(null);

        if (defaultRule == null) {
            defaultRule = new EnergyRateRule();
            defaultRule.setEnergyType(energyType);
            defaultRule.setUnitPrice(unitPrice);
            defaultRule.setStatus("ACTIVE");
            defaultRule.setRemark(remark);
            defaultRule.setDefaultRule(true);
            defaultRule = energyRateRuleRepository.save(defaultRule);
            log.info("初始化能耗计费规则: {} {}", energyType, unitPrice);
        }

        for (EnergyRateRule rule : rules) {
            boolean shouldBeDefault = rule.getId().equals(defaultRule.getId());
            if (Boolean.TRUE.equals(rule.getDefaultRule()) != shouldBeDefault) {
                rule.setDefaultRule(shouldBeDefault);
                energyRateRuleRepository.save(rule);
            }
        }

        if (!Boolean.TRUE.equals(defaultRule.getDefaultRule())) {
            defaultRule.setDefaultRule(true);
            energyRateRuleRepository.save(defaultRule);
        }
    }

    private void initDevFixtures() {
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (!devProfile) {
            return;
        }

        Tenant tenant = tenantRepository.findFirstByTenantName("德汇测试租户").orElseGet(() -> {
            Tenant created = new Tenant();
            created.setTenantName("德汇测试租户");
            created.setTenantCode("DHIC-DEV");
            created.setContactPerson("测试联系人");
            created.setContactPhone("13900000001");
            created.setStatus("ACTIVE");
            return tenantRepository.save(created);
        });

        MeetingRoom roomA = createMeetingRoomIfMissing("多功能会议室A", "德汇创新中心 3F", 40, "投影、白板、视频会议", "120.00", "180.00", "220.00");
        MeetingRoom roadshow = createMeetingRoomIfMissing("路演厅", "德汇创新中心 1F", 100, "LED大屏、音响、舞台灯光", "300.00", "450.00", "600.00");
        MeetingRoom talkRoom = createMeetingRoomIfMissing("小型洽谈室", "德汇创新中心 2F", 8, "电视、白板、茶水台", "60.00", "90.00", "120.00");
        MeetingRoom devRoom = createMeetingRoomIfMissing("开发联调会议室", "德汇创新中心 1F", 12, "投影、白板、无线网络", "80.00", "120.00", "150.00");

        createMeetingBookingIfMissing("MR-DEV-INTERNAL-PENDING", roomA, null, "内部员工", "INTERNAL", null,
                "物业服务部", java.time.LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0),
                java.time.LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0),
                "PENDING", "INTERNAL_FREE", BigDecimal.ZERO, null);
        createMeetingBookingIfMissing("MR-DEV-TENANT-CONFIRMED", roadshow, tenant.getId(), "测试联系人", "TENANT", tenant.getTenantName(),
                tenant.getTenantName(), java.time.LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0),
                java.time.LocalDateTime.now().plusDays(2).withHour(16).withMinute(0).withSecond(0).withNano(0),
                "CONFIRMED", "HOURLY", new BigDecimal("600.00"), createMeetingBillIfMissing("BILL-MR-DEV-001", tenant.getId(), new BigDecimal("600.00")));
        createMeetingBookingIfMissing("MR-DEV-CANCELLED", talkRoom, tenant.getId(), "测试联系人", "TENANT", tenant.getTenantName(),
                tenant.getTenantName(), java.time.LocalDateTime.now().plusDays(3).withHour(15).withMinute(0).withSecond(0).withNano(0),
                java.time.LocalDateTime.now().plusDays(3).withHour(16).withMinute(0).withSecond(0).withNano(0),
                "CANCELLED", "HOURLY", new BigDecimal("60.00"), null);
        createMeetingBookingIfMissing("MR-DEV-INTERNAL-CONFIRMED", devRoom, null, "系统管理员", "INTERNAL", null,
                "工程部", java.time.LocalDateTime.now().plusDays(4).withHour(9).withMinute(30).withSecond(0).withNano(0),
                java.time.LocalDateTime.now().plusDays(4).withHour(10).withMinute(30).withSecond(0).withNano(0),
                "CONFIRMED", "INTERNAL_FREE", BigDecimal.ZERO, null);

        String billNumber = "DEV-MOBILE-BILL-001";
        if (!billRepository.existsByBillNumber(billNumber)) {
            Bill bill = new Bill();
            bill.setBillNumber(billNumber);
            bill.setTenantId(tenant.getId());
            bill.setBillType("PROPERTY");
            bill.setTitle("开发联调物业费账单");
            LocalDate today = LocalDate.now();
            bill.setPeriodStart(today.withDayOfMonth(1));
            bill.setPeriodEnd(today);
            bill.setAmount(new BigDecimal("100.00"));
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setDueDate(today.plusDays(7));
            bill.setStatus("UNPAID");
            bill.setAuditStatus("APPROVED");
            bill.setApprovedBy("system");
            bill.setApprovedTime(java.time.LocalDateTime.now());
            bill.setSourceType("DEV_FIXTURE");
            bill.setRemark("开发环境联调账单");
            billRepository.save(bill);
        }
    }

    private void initEnergyFixtures() {
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (!devProfile) {
            return;
        }

        Building building = buildingRepository.findById(1L).orElse(null);
        if (building == null) {
            return;
        }

        Tenant tenant = tenantRepository.findFirstByTenantName("德汇测试租户").orElseGet(() -> {
            Tenant created = new Tenant();
            created.setTenantName("德汇测试租户");
            created.setTenantCode("DHIC-DEV");
            created.setContactPerson("测试联系人");
            created.setContactPhone("13900000001");
            created.setStatus("ACTIVE");
            return tenantRepository.save(created);
        });

        Room b202 = ensureEnergyRoom(building, "B2", -2, "B202");
        Room room801 = ensureEnergyRoom(building, "八楼", 8, "801");

        EnergyMeter b202Electric = ensureEnergyMeter("B202", "ELECTRIC", building, b202, tenant,
                "B2 B202 电井", "kWh", new BigDecimal("1"));
        EnergyMeter room801Electric = ensureEnergyMeter("801", "ELECTRIC", building, room801, tenant,
                "8F 801 房间", "kWh", new BigDecimal("1"));
        EnergyMeter b202Water = ensureEnergyMeter("B202-WATER", "WATER", building, b202, tenant,
                "B2 B202 水表间", "m³", new BigDecimal("1"));
        ensureEnergyMeter("B202-GAS", "GAS", building, b202, tenant,
                "B2 B202 燃气表", "m³", new BigDecimal("1"));

        EnergyReading b202Reading = ensureEnergyReading(b202Electric, "2026-05",
                LocalDate.of(2026, 5, 31), new BigDecimal("1000"), new BigDecimal("2200"),
                new BigDecimal("1.00"), false, null, null, "GENERATED");
        EnergyReading room801Reading = ensureEnergyReading(room801Electric, "2026-05",
                LocalDate.of(2026, 5, 31), new BigDecimal("1000"), new BigDecimal("3000"),
                new BigDecimal("1.00"), true, "HIGH_USAGE", "PENDING", "POSTED");
        ensureEnergyReading(b202Water, "2026-05",
                LocalDate.of(2026, 5, 31), new BigDecimal("200"), new BigDecimal("260"),
                new BigDecimal("4.00"), false, null, null, "NOT_GENERATED");

        ensureEnergyBill(b202Reading, "ENERGY-202605-" + b202Reading.getId(), "PENDING");
        ensureEnergyBill(room801Reading, "ENERGY-202605-" + room801Reading.getId(), "APPROVED");
    }

    private Room ensureEnergyRoom(Building building, String floorName, Integer floorNumber, String roomNumber) {
        Floor floor = floorRepository.findAll()
                .stream()
                .filter(item -> floorName.equals(item.getFloorName()) || floorNumber.equals(item.getFloorNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Floor created = new Floor();
                    created.setBuilding(building);
                    created.setFloorName(floorName);
                    created.setFloorNumber(floorNumber);
                    created.setSortOrder(floorNumber);
                    created.setStatus("ACTIVE");
                    return floorRepository.save(created);
                });

        return roomRepository.findAll()
                .stream()
                .filter(room -> roomNumber.equals(room.getRoomNumber()))
                .findFirst()
                .orElseGet(() -> {
                    Room created = new Room();
                    created.setBuilding(building);
                    created.setFloor(floor);
                    created.setRoomNumber(roomNumber);
                    created.setRoomName(roomNumber);
                    created.setArea(100.0);
                    created.setRoomType("OFFICE");
                    created.setStatus("RENTED");
                    return roomRepository.save(created);
                });
    }

    private EnergyMeter ensureEnergyMeter(String meterNo, String meterType, Building building, Room room, Tenant tenant,
                                          String location, String unit, BigDecimal multiplier) {
        return energyMeterRepository.findByMeterNo(meterNo).orElseGet(() -> {
            Long floorId = room.getFloor() == null ? null : room.getFloor().getId();
            Floor floor = floorId == null ? null : floorRepository.findById(floorId).orElse(null);
            EnergyMeter meter = new EnergyMeter();
            meter.setMeterNo(meterNo);
            meter.setMeterType(meterType);
            meter.setBuildingId(building.getId());
            meter.setBuildingName(building.getBuildingName());
            meter.setFloorId(floorId);
            meter.setFloorName(floor == null ? null : floor.getFloorName());
            meter.setRoomId(room.getId());
            meter.setRoomName(room.getRoomName() == null ? room.getRoomNumber() : room.getRoomName());
            meter.setTenantId(tenant.getId());
            meter.setTenantName(tenant.getTenantName());
            meter.setInstallLocation(location);
            meter.setUnit(unit);
            meter.setMultiplier(multiplier);
            meter.setBillingMode("BY_USAGE");
            meter.setStatus("ACTIVE");
            meter.setRemark("开发环境初始化表具");
            return energyMeterRepository.save(meter);
        });
    }

    private EnergyReading ensureEnergyReading(EnergyMeter meter, String periodMonth, LocalDate readingDate,
                                              BigDecimal previousReading, BigDecimal currentReading,
                                              BigDecimal unitPrice, boolean abnormalFlag, String abnormalReason,
                                              String abnormalStatus, String billStatus) {
        return energyReadingRepository.findAll()
                .stream()
                .filter(reading -> meter.getId().equals(reading.getMeterId()) && periodMonth.equals(reading.getPeriodMonth()))
                .findFirst()
                .orElseGet(() -> {
                    BigDecimal usage = currentReading.subtract(previousReading).multiply(meter.getMultiplier());
                    EnergyReading reading = new EnergyReading();
                    reading.setMeterId(meter.getId());
                    reading.setMeterNo(meter.getMeterNo());
                    reading.setMeterType(meter.getMeterType());
                    reading.setReadingDate(readingDate);
                    reading.setPeriodMonth(periodMonth);
                    reading.setPreviousReading(previousReading);
                    reading.setCurrentReading(currentReading);
                    reading.setMultiplier(meter.getMultiplier());
                    reading.setUsageAmount(usage);
                    reading.setUnitPrice(unitPrice);
                    reading.setSettlementAmount(usage.multiply(unitPrice));
                    reading.setUnit(meter.getUnit());
                    reading.setBuildingId(meter.getBuildingId());
                    reading.setBuildingName(meter.getBuildingName());
                    reading.setFloorId(meter.getFloorId());
                    reading.setFloorName(meter.getFloorName());
                    reading.setRoomId(meter.getRoomId());
                    reading.setRoomName(meter.getRoomName());
                    reading.setTenantId(meter.getTenantId());
                    reading.setTenantName(meter.getTenantName());
                    reading.setBillStatus(billStatus);
                    reading.setAbnormalFlag(abnormalFlag);
                    reading.setAbnormalReason(abnormalReason);
                    reading.setAbnormalStatus(abnormalFlag ? (abnormalStatus == null ? "PENDING" : abnormalStatus) : null);
                    reading.setOperatorName("system");
                    reading.setRemark("开发环境初始化抄表记录");
                    return energyReadingRepository.save(reading);
                });
    }

    private void ensureEnergyBill(EnergyReading reading, String billNumber, String auditStatus) {
        if (reading.getBillId() != null || billRepository.findBySourceTypeAndSourceId("ENERGY", reading.getId()).isPresent()) {
            return;
        }
        Bill bill = new Bill();
        bill.setBillNumber(billNumber);
        bill.setTenantId(reading.getTenantId());
        bill.setRoomId(reading.getRoomId());
        bill.setBillType("ELECTRIC".equals(reading.getMeterType()) ? "ELECTRICITY" : reading.getMeterType());
        bill.setTitle(reading.getPeriodMonth() + " 能耗账单");
        bill.setPeriodStart(LocalDate.of(2026, 5, 1));
        bill.setPeriodEnd(LocalDate.of(2026, 5, 31));
        bill.setAmount(reading.getSettlementAmount());
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(LocalDate.of(2026, 6, 7));
        bill.setStatus("UNPAID");
        bill.setAuditStatus(auditStatus);
        if ("APPROVED".equals(auditStatus)) {
            bill.setApprovedBy("system");
            bill.setApprovedTime(java.time.LocalDateTime.now());
        }
        bill.setSourceType("ENERGY");
        bill.setSourceId(reading.getId());
        bill.setRemark("初始化能耗账单，用量 " + reading.getUsageAmount() + "，单价 " + reading.getUnitPrice());
        Bill saved = billRepository.save(bill);
        reading.setBillId(saved.getId());
        reading.setBillStatus("APPROVED".equals(auditStatus) ? "POSTED" : "GENERATED");
        energyReadingRepository.save(reading);
    }

    private MeetingRoom createMeetingRoomIfMissing(String name, String location, int capacity, String facilities,
                                                   String workRate, String offRate, String holidayRate) {
        return meetingRoomRepository.findFirstByRoomName(name).orElseGet(() -> {
            MeetingRoom room = new MeetingRoom();
            room.setRoomName(name);
            room.setLocation(location);
            room.setCapacity(capacity);
            room.setFacilities(facilities);
            room.setWorkdayWorkHourRate(new BigDecimal(workRate));
            room.setWorkdayOffHourRate(new BigDecimal(offRate));
            room.setHolidayRate(new BigDecimal(holidayRate));
            room.setStatus("AVAILABLE");
            room.setRemark("开发环境初始化会议室");
            return meetingRoomRepository.save(room);
        });
    }

    private Long createMeetingBillIfMissing(String billNumber, Long tenantId, BigDecimal amount) {
        Bill bill = billRepository.findByBillNumber(billNumber).orElseGet(() -> {
            Bill created = new Bill();
            created.setBillNumber(billNumber);
            created.setTenantId(tenantId);
            created.setBillType("MEETING_ROOM");
            created.setTitle("会议室预约费 - 路演厅 - 开发初始化");
            LocalDate today = LocalDate.now();
            created.setPeriodStart(today);
            created.setPeriodEnd(today);
            created.setAmount(amount);
            created.setPaidAmount(BigDecimal.ZERO);
            created.setDueDate(today.plusDays(7));
            created.setStatus("UNPAID");
            created.setAuditStatus("PENDING");
            created.setSourceType("MEETING_ROOM");
            created.setRemark("开发环境会议预约账单");
            return billRepository.save(created);
        });
        return bill.getId();
    }

    private void createMeetingBookingIfMissing(String bookingNo, MeetingRoom room, Long tenantId, String applicantName,
                                               String sourceType, String tenantName, String department,
                                               java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
                                               String status, String feeType, BigDecimal amount, Long billingId) {
        boolean exists = meetingBookingRepository.findAll().stream()
                .anyMatch(booking -> bookingNo.equals(booking.getBookingNo()) || bookingNo.equals(booking.getBookingNumber()));
        if (exists) {
            return;
        }
        MeetingBooking booking = new MeetingBooking();
        booking.setBookingNo(bookingNo);
        booking.setBookingNumber(bookingNo);
        booking.setRoomId(room.getId());
        booking.setMeetingRoomId(room.getId());
        booking.setRoomName(room.getRoomName());
        booking.setTenantId(tenantId);
        booking.setTenantName(tenantName);
        booking.setApplicantName(applicantName);
        booking.setSourceType(sourceType);
        booking.setApplicantType(sourceType);
        booking.setDepartment(department);
        booking.setDepartmentName(department);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setStatus(status);
        booking.setFeeType(feeType);
        booking.setBillingMode("INTERNAL_FREE".equals(feeType) ? "FREE" : "HOURLY");
        booking.setAmount(amount);
        booking.setCalculatedAmount(amount);
        booking.setBillingId(billingId);
        booking.setBillId(billingId);
        booking.setRemark("开发环境初始化预约");
        booking.setCreatedBy("system");
        booking.setUpdatedBy("system");
        if ("CONFIRMED".equals(status)) {
            booking.setConfirmTime(java.time.LocalDateTime.now());
        }
        if ("CANCELLED".equals(status)) {
            booking.setCancelTime(java.time.LocalDateTime.now());
            booking.setCancelReason("开发初始化取消示例");
        }
        meetingBookingRepository.save(booking);
    }
}
