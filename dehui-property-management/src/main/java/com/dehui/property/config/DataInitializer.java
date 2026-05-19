package com.dehui.property.config;

import com.dehui.property.modules.building.entity.Building;
import com.dehui.property.modules.building.entity.Floor;
import com.dehui.property.modules.building.entity.Room;
import com.dehui.property.modules.building.repository.BuildingRepository;
import com.dehui.property.modules.building.repository.FloorRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.energy.entity.EnergyRateRule;
import com.dehui.property.modules.energy.repository.EnergyRateRuleRepository;
import com.dehui.property.modules.meeting.entity.MeetingRoom;
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
    private final TenantRepository tenantRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final BillRepository billRepository;
    private final Environment environment;

    @PostConstruct
    public void init() {
        initSystemUserAndRoles();
        initBuildingFloorsAndRooms();
        initEnergyRateRules();
        initDevFixtures();
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

        boolean hasDevRoom = meetingRoomRepository.findAll()
                .stream()
                .anyMatch(room -> "开发联调会议室".equals(room.getRoomName()));
        if (!hasDevRoom) {
            MeetingRoom room = new MeetingRoom();
            room.setRoomName("开发联调会议室");
            room.setLocation("德汇创新中心 1F");
            room.setCapacity(12);
            room.setFacilities("投影、白板、无线网络");
            room.setWorkdayWorkHourRate(new BigDecimal("80.00"));
            room.setWorkdayOffHourRate(new BigDecimal("120.00"));
            room.setHolidayRate(new BigDecimal("150.00"));
            room.setStatus("ACTIVE");
            meetingRoomRepository.save(room);
        }

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
}
