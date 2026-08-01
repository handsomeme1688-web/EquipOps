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
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

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
            Map.entry(".xls",  List.of(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                                                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1})),
            Map.entry(".docx", List.of(new byte[]{0x50, 0x4B, 0x03, 0x04})),
            Map.entry(".xlsx", List.of(new byte[]{0x50, 0x4B, 0x03, 0x04}))
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceFileVO upload(Long deviceId, MultipartFile file) {
        if(file==null || file.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST);

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件大小不能超过 10MB");
        }

        Device device = deviceService.getById(deviceId);
        if(device==null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);

        if(!isAdmin(UserContext.getUserId()) && !device.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);
        }



        String contentType = file.getContentType();
        if(!ALLOWED_MIME_TYPES.contains(contentType)) throw new BizException(ResultCode.BAD_REQUEST);

        String originalFilename = file.getOriginalFilename();
        if(originalFilename==null) throw new BizException(ResultCode.BAD_REQUEST);
        int index = originalFilename.lastIndexOf(".");
        if(index==-1) throw new BizException(ResultCode.BAD_REQUEST);
        String ext = originalFilename.substring(index);
        if(!ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) throw new BizException(ResultCode.BAD_REQUEST);

        try(InputStream is = file.getInputStream()){
            byte[] header = is.readNBytes(8);
            if(!matchMagicNumbers(ext,header)) throw new BizException(ResultCode.BAD_REQUEST);
        }catch (Exception e){
            throw new BizException(ResultCode.BAD_REQUEST);
        }



        String storageKey = java.util.UUID.randomUUID()+ext;
        Path filePath = Path.of(UPLOAD_DIR,storageKey);
        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        }catch (IOException e){
            throw new BizException(ResultCode.BAD_REQUEST);
        }

        DeviceFile df = new DeviceFile();
        df.setDeviceId(device.getId());
        df.setFileName(file.getOriginalFilename());
        df.setStorageKey(storageKey);
        df.setSize(file.getSize());
        df.setContentType(file.getContentType());
        df.setUploadBy(UserContext.getUserId());
        df.setUploadTime(java.time.LocalDateTime.now());
        save(df);

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
        DeviceFile deviceFile = getById(fileId);
        if (deviceFile==null) throw new BizException(ResultCode.NOT_FOUND);

        Device device = deviceService.getById(deviceFile.getDeviceId());
        if (device==null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        if(!isAdmin(UserContext.getUserId()) && !device.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);
        }
        Path filePath = Path.of(UPLOAD_DIR, deviceFile.getStorageKey());

        byte[] bytes = Files.readAllBytes(filePath);

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

    private boolean isAdmin(Long userId){
        return permissionCheckService.hasPerm(userId, "system:role:manage");
    }

    private boolean matchMagicNumbers(String ext,byte[] header){
        List<byte[]> allowedMagicNumbers = MAGIC_NUMBERS.get(ext.toLowerCase());
        if (allowedMagicNumbers==null) return false;
        for (byte[] magicNumber : allowedMagicNumbers){
            if(startsWith(header,magicNumber)) return true;
        }
        return false;

    }

    private boolean startsWith(byte[] header,byte[] magicNumbers){
        if(header.length<magicNumbers.length) return false;
        for (int i = 0; i < magicNumbers.length; i++) {
            if(header[i]!=magicNumbers[i]) return false;
        }
        return true;
    }
}
