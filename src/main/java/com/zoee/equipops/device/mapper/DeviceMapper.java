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

    /** 分页查询设备，并关联部门名和责任人名。 */
    IPage<DeviceVO> selectDeviceVoPage(IPage<DeviceVO> page, @Param("q") DeviceQuery q);
}
