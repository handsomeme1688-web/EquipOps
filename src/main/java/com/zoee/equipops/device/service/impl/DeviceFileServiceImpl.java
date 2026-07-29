package com.zoee.equipops.device.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.auth.service.PermissionCheckService;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.domain.entity.DeviceFile;
import com.zoee.equipops.device.domain.vo.DeviceFileVO;
import com.zoee.equipops.device.mapper.DeviceFileMapper;
import com.zoee.equipops.device.service.DeviceFileService;
import com.zoee.equipops.device.service.DeviceService;
import com.zoee.equipops.system.entity.User;
import com.zoee.equipops.system.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceFileServiceImpl extends ServiceImpl<DeviceFileMapper, DeviceFile> implements DeviceFileService {
    private final DeviceService deviceService;
    private final UserService userService;
    private final PermissionCheckService permissionCheckService;
    private static final String UPLOAD_DIR = "F:/uploads";
    // 扩展名白名单
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp",   // 图片
            ".pdf", ".doc", ".docx", ".xls", ".xlsx"  // 文档
//            ".txt"                                       // 纯文本。踢掉
    );

    // MIME类型白名单
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",                                        // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
//            "text/plain"
    );

    // 魔数
    private static final Map<String, List<byte[]>> MAGIC_NUMBERS = Map.ofEntries(
            Map.entry(".jpg",  List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})),
            Map.entry(".jpeg", List.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})),
            Map.entry(".png",  List.of(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})),
            Map.entry(".gif",  List.of(
                    new byte[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61},
                    new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61}
            )),
            Map.entry(".bmp",  List.of(new byte[]{0x42, 0x4D})),
            Map.entry(".pdf",  List.of(new byte[]{0x25, 0x50, 0x44, 0x46})),
            Map.entry(".doc",  List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                                                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1})),
//            Map.entry(".dot",  List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
//                                                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1})),
            Map.entry(".xls",  List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                                                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1})),
//            Map.entry(".xlt",  List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
//                                                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1})),
            Map.entry(".docx", List.of(new byte[]{0x50, 0x4B, 0x03, 0x04})),
//            Map.entry(".dotx", List.of(new byte[]{0x50, 0x4B, 0x03, 0x04})),
            Map.entry(".xlsx", List.of(new byte[]{0x50, 0x4B, 0x03, 0x04}))
//            Map.entry(".xltx", List.of(new byte[]{0x50, 0x4B, 0x03, 0x04}))
    );// .txt 无固定魔数，纯文本没有标准文件头

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceFileVO upload(Long deviceId, MultipartFile file) {
        // 1. 空文件校验
        if(file==null || file.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST);

        // 2. 文件大小校验
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件大小不能超过 10MB");
        }

        // 3. 设备存在校验
        Device device = deviceService.getById(deviceId);
        if(device==null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);

        // 4. 人员权限校验
        if(!isAdmin(UserContext.getUserId()) && !device.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);
        }



        // 5. MIME类型白名单
        String contentType = file.getContentType();
        if(!ALLOWED_MIME_TYPES.contains(contentType)) throw new BizException(ResultCode.BAD_REQUEST);

        // 6. 扩展名校验
        String originalFilename = file.getOriginalFilename();
        if(originalFilename==null) throw new BizException(ResultCode.BAD_REQUEST);
        int index = originalFilename.lastIndexOf(".");
        if(index==-1) throw new BizException(ResultCode.BAD_REQUEST);
        String ext = originalFilename.substring(index);
        if(!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) throw new BizException(ResultCode.BAD_REQUEST);

        // 7. 魔数，防改后缀.不管文件名改成什么，文件内容的前几个字节是改不掉的
        try(InputStream is = file.getInputStream()){
            byte[] header = is.readNBytes(8);
            if(!matchMagicNumbers(ext,header)) throw new BizException(ResultCode.BAD_REQUEST);
        }catch (Exception e){
            throw new BizException(ResultCode.BAD_REQUEST);
        }



        // 保存文件到磁盘
        String storageKey = java.util.UUID.randomUUID()+ext;
        Path filePath = Path.of(UPLOAD_DIR,storageKey);
        try {
            Files.createDirectories(filePath.getParent());
//            Files.write(filePath,file.getBytes()); //文件多大就占多大内存。10 个用户同时上传 50MB 的 PDF，堆里同时挂着 500MB。大文件或高并发下直接 OOM。
            file.transferTo(filePath.toFile()); // 文件再大也只占一个 buffer 大小的内存。这是 Spring 推荐的方式，MultipartFile 接口专门给了这个方法。
        }catch (IOException e){
            throw new BizException(ResultCode.BAD_REQUEST);
        }

        // 写入数据库
        DeviceFile df = new DeviceFile();
        df.setDeviceId(device.getId());
        df.setFileName(file.getOriginalFilename());
        df.setStorageKey(storageKey);
        df.setSize(file.getSize());
        df.setContentType(file.getContentType());
        df.setUploadBy(UserContext.getUserId());
        df.setUploadTime(java.time.LocalDateTime.now());
        save(df);

        // 返回VO
        DeviceFileVO deviceFileVO = new DeviceFileVO();
        deviceFileVO.setId(df.getId());
        deviceFileVO.setFileName(file.getOriginalFilename());
        deviceFileVO.setSize(file.getSize());
        deviceFileVO.setContentType(file.getContentType());

        User user = userService.getById(UserContext.getUserId());
        deviceFileVO.setUploadByName(user.getRealName());
        deviceFileVO.setUploadTime(java.time.LocalDateTime.now());
        return deviceFileVO;
    }

    @Override
    public void download(Long fileId, HttpServletResponse response) throws IOException {
        // 查设备文件
        DeviceFile deviceFile = getById(fileId);
        if (deviceFile==null) throw new BizException(ResultCode.NOT_FOUND);

        // 查文件关联的设备
        Device device = deviceService.getById(deviceFile.getDeviceId());
        if (device==null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        if(!isAdmin(UserContext.getUserId()) && !device.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);
        }
        // 读磁盘文件
        // 1. 拼出文件的磁盘路径
        Path filePath = Path.of(UPLOAD_DIR, deviceFile.getStorageKey());

        // 2. 读进内存
        byte[] bytes = Files.readAllBytes(filePath);

        // 3. 写进响应流
        response.setContentType(deviceFile.getContentType());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + deviceFile.getFileName() + "\"");
        response.setContentLengthLong(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId) {
        DeviceFile deviceFile = getById(fileId);
        if(deviceFile==null) throw new BizException(ResultCode.NOT_FOUND);
        Device device = deviceService.getById(deviceFile.getDeviceId());
        if(device==null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        if(!isAdmin(UserContext.getUserId()) && !UserContext.getDeptId().equals(device.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);
        }
        try {
            Path filePath = Path.of(UPLOAD_DIR,deviceFile.getStorageKey());
            Files.deleteIfExists(filePath);
        }catch (IOException e){
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        removeById(fileId);
    }

    @Override
    public List<DeviceFileVO> listByDeviceId(Long deviceId) {
        Device device = deviceService.getById(deviceId);
        if (device== null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        if(!isAdmin(UserContext.getUserId()) && !UserContext.getDeptId().equals(device.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);
        }
        List<DeviceFile> fils= lambdaQuery()
                .eq(DeviceFile::getDeviceId,deviceId)
                .orderByDesc(DeviceFile::getUploadTime)
                .list();
        return fils.stream()
                .map(df->{
                    DeviceFileVO deviceFileVO = new DeviceFileVO();
                    deviceFileVO.setId(df.getId());
                    deviceFileVO.setFileName(df.getFileName());
                    deviceFileVO.setSize(df.getSize());
                    deviceFileVO.setContentType(df.getContentType());
                    User user = userService.getById(df.getUploadBy());
                    deviceFileVO.setUploadByName(user!=null ? user.getRealName() : "未知");
                    deviceFileVO.setUploadTime(df.getUploadTime());
                    return deviceFileVO;
                })
                .toList();
    }

    /**
     * 判断用户是否是管理员
     * @param userId
     * @return
     */
    private boolean isAdmin(Long userId){
        return permissionCheckService.hasPerm(userId, "system:role:manage");
    }

    /**
     * 校验文件扩展名与文件头是否匹配
     * @param ext
     * @param header
     * @return
     */
    private boolean matchMagicNumbers(String ext,byte[] header){
//        if (ext.equalsIgnoreCase(".txt")) return true;
        List<byte[]> allowedMagicNumbers = MAGIC_NUMBERS.get(ext.toLowerCase());
        if (allowedMagicNumbers==null) return false;
        for (byte[] magicNumber : allowedMagicNumbers){
            if(startsWith(header,magicNumber)) return true;
        }
        return false;

    }

    /**
     * 判断文件头是否以指定的魔术数字开头
     * @param header
     * @param magicNumbers
     * @return
     */
    private boolean startsWith(byte[] header,byte[] magicNumbers){
        if(header.length<magicNumbers.length) return false;
        for (int i = 0; i < magicNumbers.length; i++) {
            if(header[i]!=magicNumbers[i]) return false;
        }
        return true;
    }
}
