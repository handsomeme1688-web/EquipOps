package com.zoee.equipops.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.domain.query.DeviceQuery;
import com.zoee.equipops.device.domain.vo.DeviceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     *   Controller 接收前端请求（页码 + 条件）
     *          ↓
     *   Service 构造 Page 对象 + DeviceQuery
     *          ↓
     *   调用 Mapper.selectDeviceVoPage(page, query)
     *          ↓
     *   MyBatis-Plus 拦截 → 自动分页（拼 LIMIT + 查 COUNT）
     *          ↓
     *   返回 IPage<DeviceVO>（含数据 + 分页信息）
     *          ↓
     *   前端拿到列表 + 总页数，渲染分页组件
     *
     * 分页查询设备，并 JOIN 出部门名、责任人名（三表关联）。
     *
     * 第一个参数必须是 IPage：MyBatis-Plus 的分页拦截器一看到它，
     * 就会自动在你 XML 的 SQL 末尾拼 LIMIT，并额外跑一条 count 查询。
     * 所以 XML 里你只写 JOIN 和 WHERE，LIMIT / count 都不用管。
     *
     * @param page 分页参数（第几页、每页几条）；泛型 DeviceVO 表示每行映射成 VO。又当输入又当输出，包含分页信息
     * @param q    检索条件；@Param("q") 之后，XML 里用 #{q.name}、#{q.status} 取值
     * @return 当前页的 DeviceVO 列表（含总数、页码等分页信息）
     */


    IPage<DeviceVO> selectDeviceVoPage(IPage<DeviceVO> page, @Param("q") DeviceQuery q);
}
