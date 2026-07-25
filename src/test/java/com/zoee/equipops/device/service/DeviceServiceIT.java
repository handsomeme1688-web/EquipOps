package com.zoee.equipops.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.domain.query.DeviceQuery;
import com.zoee.equipops.device.domain.vo.DeviceVO;
import com.zoee.equipops.device.enums.DeviceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 9 集成测试：组合查询、索引与 SQL 证据。
 *
 * <p>用 Testcontainers 起真 MySQL，验证：
 * <ol>
 *   <li>单条件命中 —— 名称模糊、编号精确、状态、部门</li>
 *   <li>多条件组合命中 —— 部门+状态 / 名称+部门 / 名称+状态 / 三条件</li>
 *   <li>空条件返回全部分页</li>
 *   <li>JOIN 出的 VO 里 deptName / ownerName 不为 null</li>
 * </ol>
 *
 * <p>测试数据构造绕过 {@link DeviceService#create} 的硬编码 deptId，
 * 直接走 {@code IService.save()}，确保两个不同部门的设备都能造出来。
 * 类级 @Transactional 保证每个测试方法执行后自动回滚，测试间互不污染。
 *
 * @author zoe
 * @since 2026-07-25 Day 9
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("设备组合查询集成测试（Day 9）")
class DeviceServiceIT {

    @Autowired
    private DeviceService deviceService;

    // ──────────────── 测试数据 ────────────────

    private Device deviceDept2Normal;
    private Device deviceDept2Repairing;
    private Device deviceDept3Normal;
    private Device deviceDept3Disabled;

    @BeforeEach
    void setUp() {
        // 第一生产车间 (deptId=2)、责任人张三 (ownerId=2)
        deviceDept2Normal = saveDevice("DEV-001", "注塑机Alpha", "ZX-100",
                "一车间A区", 2L, 2L, DeviceStatus.NORMAL);
        deviceDept2Repairing = saveDevice("DEV-002", "注塑机Beta", "ZX-200",
                "一车间B区", 2L, 2L, DeviceStatus.REPAIRING);
        // 第二生产车间 (deptId=3)、责任人李四 (ownerId=3)
        deviceDept3Normal = saveDevice("DEV-003", "数控铣床Gamma", "CNC-500",
                "二车间A区", 3L, 3L, DeviceStatus.NORMAL);
        deviceDept3Disabled = saveDevice("DEV-004", "冲压机Delta", "CY-10",
                "二车间B区", 3L, 3L, DeviceStatus.DISABLED);
    }

    /**
     * 直接调 IService.save() 插入设备。
     * 不走 DeviceService.create：create 内部 deptId 硬编码为 2L（Day 11 才切到 UserContext），
     * 且含编号重复校验——测试数据构造应走最短路径。
     */
    private Device saveDevice(String code, String name, String model,
                              String location, Long deptId, Long ownerId,
                              DeviceStatus status) {
        Device device = new Device();
        device.setCode(code);
        device.setName(name);
        device.setModel(model);
        device.setLocation(location);
        device.setDeptId(deptId);
        device.setOwnerId(ownerId);
        device.setStatus(status);
        deviceService.save(device);
        return device;
    }

    // ════════════════ ① 单条件命中 ════════════════

    @Nested
    @DisplayName("① 单条件筛选")
    class SingleCondition {

        @Test
        @DisplayName("按名称模糊匹配 -> 2 条含「注塑」的设备")
        void shouldFilterByNameLike() {
            DeviceQuery query = new DeviceQuery();
            query.setName("注塑");

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getRecords())
                    .extracting(DeviceVO::getName)
                    .containsExactlyInAnyOrder("注塑机Alpha", "注塑机Beta");
        }

        @Test
        @DisplayName("按编号精确匹配 → 1 条")
        void shouldFilterByCodeExact() {
            DeviceQuery query = new DeviceQuery();
            query.setCode("DEV-003");

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(1);
            assertThat(page.getRecords().get(0).getCode()).isEqualTo("DEV-003");
        }

        @Test
        @DisplayName("按状态筛选 → 2 条 NORMAL")
        void shouldFilterByStatus() {
            DeviceQuery query = new DeviceQuery();
            query.setStatus(DeviceStatus.NORMAL);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getRecords())
                    .extracting(DeviceVO::getStatus)
                    .containsOnly(DeviceStatus.NORMAL);
        }

        @Test
        @DisplayName("按部门筛选 → 2 条属于第一生产车间")
        void shouldFilterByDeptId() {
            DeviceQuery query = new DeviceQuery();
            query.setDeptId(2L);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getRecords())
                    .extracting(DeviceVO::getDeptId)
                    .containsOnly(2L);
        }
    }

    // ════════════════ ② 多条件组合命中 ════════════════

    @Nested
    @DisplayName("② 多条件组合筛选")
    class MultipleConditions {

        @Test
        @DisplayName("部门 + 状态 → 仅 DEV-001")
        void shouldFilterByDeptAndStatus() {
            DeviceQuery query = new DeviceQuery();
            query.setDeptId(2L);
            query.setStatus(DeviceStatus.NORMAL);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(1);
            assertThat(page.getRecords().get(0).getCode()).isEqualTo("DEV-001");
        }

        @Test
        @DisplayName("名称 + 部门 -> 第二生产车间的 2 台含「机」设备")
        void shouldFilterByNameAndDept() {
            DeviceQuery query = new DeviceQuery();
            query.setName("机");
            query.setDeptId(3L);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getRecords())
                    .extracting(DeviceVO::getName)
                    .containsExactlyInAnyOrder("数控铣床Gamma", "冲压机Delta");
        }

        @Test
        @DisplayName("名称 + 状态 → 注塑 + 维修中 = DEV-002")
        void shouldFilterByNameAndStatus() {
            DeviceQuery query = new DeviceQuery();
            query.setName("塑");
            query.setStatus(DeviceStatus.REPAIRING);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(1);
            assertThat(page.getRecords().get(0).getCode()).isEqualTo("DEV-002");
        }

        @Test
        @DisplayName("三条件：部门 3 + DISABLED + 名称含「冲压」-> DEV-004")
        void shouldFilterByThreeConditions() {
            DeviceQuery query = new DeviceQuery();
            query.setDeptId(3L);
            query.setStatus(DeviceStatus.DISABLED);
            query.setName("冲压");

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(1);
            assertThat(page.getRecords().get(0).getCode()).isEqualTo("DEV-004");
        }
    }

    // ════════════════ ③ 空条件 + 分页 ════════════════

    @Nested
    @DisplayName("③ 空条件与分页")
    class EmptyQueryAndPagination {

        @Test
        @DisplayName("空查询 → 全部 4 条")
        void shouldReturnAllWhenQueryIsEmpty() {
            DeviceQuery query = new DeviceQuery();

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(4);
            assertThat(page.getRecords()).hasSize(4);
        }

        @Test
        @DisplayName("pageSize=2 → 第一页 2 条，总页数 2")
        void shouldPaginateCorrectly() {
            DeviceQuery query = new DeviceQuery();
            query.setPageNum(1);
            query.setPageSize(2);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(4);
            assertThat(page.getRecords()).hasSize(2);
            assertThat(page.getPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("翻到第二页 → 剩余 2 条")
        void shouldReturnRemainingOnSecondPage() {
            DeviceQuery query = new DeviceQuery();
            query.setPageNum(2);
            query.setPageSize(2);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(4);
            assertThat(page.getRecords()).hasSize(2);
            assertThat(page.getCurrent()).isEqualTo(2);
        }

        @Test
        @DisplayName("超出范围的页码 → 空列表，total 仍为 4")
        void shouldReturnEmptyPageWhenOutOfRange() {
            DeviceQuery query = new DeviceQuery();
            query.setPageNum(10);
            query.setPageSize(10);

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getTotal()).isEqualTo(4);
            assertThat(page.getRecords()).isEmpty();
        }
    }

    // ════════════════ ④ JOIN 嵌套字段 ════════════════

    @Nested
    @DisplayName("④ JOIN 嵌套字段验证")
    class JoinedFields {

        @Test
        @DisplayName("deptName 不为 null -- DEV-001 所属「第一生产车间」")
        void shouldPopulateDeptNameFromJoin() {
            DeviceQuery query = new DeviceQuery();
            query.setCode("DEV-001");

            Page<DeviceVO> page = deviceService.page(query);

            DeviceVO vo = page.getRecords().get(0);
            assertThat(vo.getDeptName())
                    .as("LEFT JOIN dept 后部门名应正确填充")
                    .isEqualTo("第一生产车间");
        }

        @Test
        @DisplayName("ownerName 不为 null -- DEV-001 责任人「张三」")
        void shouldPopulateOwnerNameFromJoin() {
            DeviceQuery query = new DeviceQuery();
            query.setCode("DEV-001");

            Page<DeviceVO> page = deviceService.page(query);

            DeviceVO vo = page.getRecords().get(0);
            assertThat(vo.getOwnerName())
                    .as("LEFT JOIN user 后责任人姓名应正确填充")
                    .isEqualTo("张三");
        }

        @Test
        @DisplayName("不同部门设备的 deptName 各自正确，无 null")
        void shouldJoinCorrectDeptPerDevice() {
            DeviceQuery query = new DeviceQuery();

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getRecords())
                    .extracting(DeviceVO::getDeptName)
                    .doesNotContainNull()
                    .contains("第一生产车间", "第二生产车间");
        }

        @Test
        @DisplayName("不同责任人的 ownerName 各自正确，无 null")
        void shouldJoinCorrectOwnerPerDevice() {
            DeviceQuery query = new DeviceQuery();

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getRecords())
                    .extracting(DeviceVO::getOwnerName)
                    .doesNotContainNull()
                    .contains("张三", "李四");
        }

        @Test
        @DisplayName("deptName 是 String 而非嵌套对象 —— VO 扁平化")
        void shouldHaveFlatDeptNameNotNestedObject() {
            DeviceQuery query = new DeviceQuery();
            query.setCode("DEV-001");

            Page<DeviceVO> page = deviceService.page(query);

            assertThat(page.getRecords().get(0).getDeptName())
                    .isInstanceOf(String.class);
        }
    }
}
