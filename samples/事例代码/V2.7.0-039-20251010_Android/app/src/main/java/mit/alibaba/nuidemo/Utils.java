package mit.alibaba.nuidemo;

import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    private static final String TAG = "DemoUtils";

    public static int createDir (String dirPath) {
        File dir = new File(dirPath);
        //文件夹是否已经存在
        if (dir.exists()) {
            Log.w(TAG,"The directory [ " + dirPath + " ] has already exists");
            return 1;
        }

        if (!dirPath.endsWith(File.separator)) {//不是以 路径分隔符 "/" 结束，则添加路径分隔符 "/"
            dirPath = dirPath + File.separator;
        }

        //创建文件夹
        if (dir.mkdirs()) {
            Log.d(TAG,"create directory [ "+ dirPath + " ] success");
            return 0;
        }

        Log.e(TAG,"create directory [ "+ dirPath + " ] failed");
        return -1;
    }

//    public static String ip = "";
//    public static String getDirectIp() {
//        Log.i(TAG, "direct ip is " + Utils.ip);
//        Thread th = new Thread(){
//            @Override
//            public void run() {
//                try {
//                    InetAddress addr = InetAddress.getByName("nls-gateway-inner.aliyuncs.com");
//                    Utils.ip = addr.getHostAddress();
//                    Log.i(TAG, "direct ip is " + Utils.ip);
//                } catch (UnknownHostException e) {
//                    e.printStackTrace();
//                }
//            }
//
//        };
//        th.start();
//        try {
//            th.join(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return ip;
//    }

    public static String getMsgWithErrorCode(int code, String status) {
        String str = "错误码:" + code;
        switch (code) {
            case 140001:
                str += " 错误信息: 引擎未创建, 请检查是否成功初始化, 详情可查看运行日志.";
                break;
            case 140008:
                str += " 错误信息: 鉴权失败, 请关注日志中详细失败原因.";
                break;
            case 140011:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause接口.";
                break;
            case 140013:
                str += " 错误信息: 当前方法调用不符合当前状态, 比如在未初始化情况下调用pause/release等接口.";
                break;
            case 140900:
                str += " 错误信息: tts引擎初始化失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140901:
                str += " 错误信息: tts引擎初始化失败, 请检查使用的SDK是否支持离线语音合成功能.";
                break;
            case 140903:
                str += " 错误信息: tts引擎任务创建失败, 请检查资源路径和资源文件是否正确.";
                break;
            case 140908:
                str += " 错误信息: 发音人资源无法获得正确采样率, 请检查发音人资源是否正确.";
                break;
            case 140910:
                str += " 错误信息: 发音人资源路径无效, 请检查发音人资源文件路径是否正确.";
                break;
            case 144002:
                str += " 错误信息: 若发生于语音合成, 可能为传入文本超过16KB. 可升级到最新版本, 具体查看日志确认.";
                break;
            case 144003:
                str += " 错误信息: token过期或无效, 请检查token是否有效.";
                break;
            case 144004:
                str += " 错误信息: 语音合成超时, 具体查看日志确认.";
                break;
            case 144006:
                str += " 错误信息: 云端返回未分类错误, 请看详细的错误信息.";
                break;
            case 144103:
                str += " 错误信息: 设置参数无效, 请参考接口文档检查参数是否正确, 也可通过task_id咨询客服.";
                break;
            case 144505:
                str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置参数及服务地址.";
                break;
            case 170008:
                str += " 错误信息: 鉴权成功, 但是存储鉴权信息的文件路径不存在或无权限.";
                break;
            case 170806:
                str += " 错误信息: 请设置SecurityToken.";
                break;
            case 170807:
                str += " 错误信息: SecurityToken过期或无效, 请检查SecurityToken是否有效.";
                break;
            case 240002:
                str += " 错误信息: 设置的参数不正确, 比如设置json参数格式不对, 设置的文件无效等.";
                break;
            case 240005:
                if (status == "init") {
                    str += " 错误信息: 请检查appkey、akId、akSecret、url等初始化参数是否无效或空.";
                } else {
                    str += " 错误信息: 传入参数无效, 请检查参数正确性.";
                }
                break;
            case 240008:
                str += " 错误信息: SDK内部核心引擎未成功初始化.";
                break;
            case 240011:
                str += " 错误信息: SDK未成功初始化.";
                break;
            case 240040:
                str += " 错误信息: 本地引擎初始化失败，可能是资源文件(如kws.bin)损坏.";
                break;
            case 240052:
                str += " 错误信息: 2s未传入音频数据，请检查录音相关代码、权限或录音模块是否被其他应用占用.";
                break;
            case 240063:
                str += " 错误信息: SSL错误，可能为SSL建连失败。比如token无效或者过期，或SSL证书校验失败(可升级到最新版)等等，具体查日志确认.";
                break;
            case 240068:
                str += " 错误信息: 403 Forbidden, token无效或者过期.";
                break;
            case 240070:
                str += " 错误信息: 鉴权失败, 请查看日志确定具体问题, 特别是关注日志 E/iDST::ErrMgr: errcode=.";
                break;
            case 240072:
                str += " 错误信息: 录音文件识别传入的录音文件不存在.";
                break;
            case 240073:
                str += " 错误信息: 录音文件识别传入的参数错误, 比如audio_address不存在或file_path不存在或其他参数错误.";
                break;
            case 240075:
                str += " 错误信息: 录音文件识别失败，比如账号无权限等.";
                break;
            case 10000016:
                if (status.contains("403 Forbidden")) {
                    str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置的账号临时凭证.";
                } else if (status.contains("404 Forbidden")) {
                    str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置的服务地址URL.";
                } else {
                    str += " 错误信息: 流式语音合成未成功连接服务, 请检查设置的参数及服务地址.";
                }
                break;
            case 40000004:
                str += " 错误信息: 长时间未收到指令或音频.";
                break;
            case 40000010:
                str += " 错误信息: 此账号试用期已过, 请开通商用版或检查账号权限.";
                break;
            case 41010105:
                str += " 错误信息: 长时间未收到人声，触发静音超时.";
                break;
            case 999999:
                str += " 错误信息: 库加载失败, 可能是库不支持当前activity, 或库加载时崩溃, 可详细查看日志判断.";
                break;
            default:
                str += " 未知错误信息, 请查看官网错误码和运行日志确认问题.";
        }
        return str;
    }

    public static boolean isExist(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            Log.e(TAG, "打不开：" + filename);
            return false;
        } else {
            return true;
        }
    }

    public static boolean isFileExists(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }

    public static String extractVersion(String input) {
        if (input.isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile("-(\\w+)-");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    // 此处只为DEMO中离线语音合成演示，实际产品中不保证下载链接不会变化，请客户自行管理离线语音包
    private static final Map<String, String> boutiquevoice_files_map = new HashMap<String, String>() {{
        put("aijia", "https://gw.alipayobjects.com/os/bmw-prod/a9f6fd18-cf0c-45a0-83b0-718ccfa36212.zip");
        put("aicheng", "https://gw.alipayobjects.com/os/bmw-prod/15b64d3f-ee9b-409a-bac6-e319596dfe91.zip");
        put("aiqi", "https://gw.alipayobjects.com/os/bmw-prod/b7b1152b-0174-44e9-88a8-2525695eb45c.zip");
        put("aida", "https://gw.alipayobjects.com/os/bmw-prod/5b44533f-0d00-43f6-8bbc-f8752afec4df.zip");
        put("aihao", "https://gw.alipayobjects.com/os/bmw-prod/d95c5709-8a2f-4473-959d-08a8a1f0019c.zip");
        put("aishuo", "https://gw.alipayobjects.com/os/bmw-prod/1b4b3829-b95c-411c-b960-21f0cef77fc9.zip");
        put("aiying", "https://gw.alipayobjects.com/os/bmw-prod/ce7b0092-51e3-41f6-9119-0f0511355f80.zip");
        put("aitong", "https://gw.alipayobjects.com/os/bmw-prod/5637b419-1515-46f5-b9bf-52b381e0a3a8.zip");
        put("abby", "https://gw.alipayobjects.com/os/bmw-prod/de45872e-f2a4-4c75-8c9c-6cb1570cf39e.zip");
        put("andy", "https://gw.alipayobjects.com/os/bmw-prod/3798c839-c5e6-4ff1-b69e-004bbf51be64.zip");
        put("annie", "https://gw.alipayobjects.com/os/bmw-prod/96affc9e-9a20-4dee-8ae7-a0aa8953493c.zip");
    }};
    private static final Map<String, String> standard_voice_files_map = new HashMap<String, String>() {{
        put("aijia", "https://gw.alipayobjects.com/os/bmw-prod/a9f6fd18-cf0c-45a0-83b0-718ccfa36212.zip");
        put("aicheng", "https://gw.alipayobjects.com/os/bmw-prod/15b64d3f-ee9b-409a-bac6-e319596dfe91.zip");
        put("xiaoyun", "https://gw.alipayobjects.com/os/bmw-prod/43a0c626-c40d-4762-92b0-2c0e1fa8ef79.zip");
        put("xiaoda", "https://gw.alipayobjects.com/os/bmw-prod/60d0b806-6518-4cb1-91b4-6807fd8d3d49.zip");
        put("xiaogang", "https://gw.alipayobjects.com/os/bmw-prod/329c607b-3235-4fd0-8bc2-c87ada55bb36.zip");
        put("xiaoqi", "https://gw.alipayobjects.com/os/bmw-prod/591aab02-82e2-4774-b76b-91c80e41813d.zip");
        put("xiaoxia", "https://gw.alipayobjects.com/os/bmw-prod/a346db66-357a-4708-8ad6-c7f1e6a9691d.zip");
    }};
    public static Map<String, String> getVoiceFilesMap(String sdk_code) {
        if (sdk_code.equals("software_nls_tts_offline_standard")) {
            return standard_voice_files_map;
        } else if (sdk_code.equals("software_nls_tts_offline")) {
            return boutiquevoice_files_map;
        } else {
            return standard_voice_files_map;
        }
    }
    public static boolean downloadZipFile(String font_name, String link, String path) {
        String targetDir = path;
        String targetFile = targetDir + "/" + font_name;
        String targetZip = targetFile + ".zip";
        File targetPath = new File(targetFile);
        if (targetPath.exists()) {
            Log.i(TAG, font_name + " is existent.");
            return true;
        } else {
            // downloading ...
            try {
                URL url = new URL(link);
                Log.i(TAG, "url link: " + link);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();

                // 检查服务器的响应
                if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Http URL connect failed " + urlConnection.getResponseCode());
                    return false;
                }

                InputStream inputStream = urlConnection.getInputStream();
                FileOutputStream fileOutputStream = new FileOutputStream(targetZip);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }

                // 关闭流
                fileOutputStream.flush();
                fileOutputStream.close();
                inputStream.close();

                try {
                    unzip(targetZip, targetDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String md5 = getFileMD5(targetPath);
                Log.i(TAG, "File:" + targetPath + "  md5:" + md5);

                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    public static String getFileMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                md5.update(buffer, 0, read);
            }

            byte[] digest = md5.digest();
            return bytesToHex(digest);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        return destFile;
    }
    private static void unzip(String zipFilePath, String destDirectory) throws IOException {
        File dir = new File(destDirectory);
        // 创建解压目标文件夹
        if (!dir.exists()) {
            dir.mkdir();
        }

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry zipEntry = zis.getNextEntry();

        while (zipEntry != null) {
            File newFile = newFile(dir, zipEntry);
            if (zipEntry.isDirectory()) {
                newFile.mkdir();
            } else {
                // 确保文件的父目录存在
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    private static String getFileNameFromUrl(HttpURLConnection connection) {
        String fileName = null;

        // 从 Content-Disposition header 获取文件名
        String disposition = connection.getHeaderField("Content-Disposition");
        if (disposition != null) {
            String[] parts = disposition.split(";");
            for (String part : parts) {
                if (part.trim().startsWith("filename")) {
                    fileName = part.substring(part.indexOf('=') + 2, part.length() - 1);
                    break;
                }
            }
        }

        // 如果没有找到文件名，可以从 URL 中提取文件名
        if (fileName == null) {
            String urlPath = connection.getURL().getPath();
            fileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);
        }

        // 使用默认文件名，如果实在无法获取
        if (fileName == null || fileName.isEmpty()) {
            fileName = "downloaded_file"; // 默认文件名
        }

        return fileName;
    }

    public static String downloadFile(String link, String dir) {
        String targetDir = dir;

        // downloading ...
        try {
            URL url = new URL(link);
            Log.i(TAG, "url link: " + link);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();

            // 检查服务器的响应
            if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Http URL connect failed " + urlConnection.getResponseCode());
                return "";
            }

            InputStream inputStream = urlConnection.getInputStream();
            String fileName = getFileNameFromUrl(urlConnection);
            if (fileName == null) {
                Log.e(TAG, "Cannot get file name from url");
                return "";
            } else {
                Log.i(TAG, "Get file name from url " + fileName);
            }

            File targetPath = new File(targetDir, fileName);
            String targetFilePath = targetPath.getPath();
            if (targetPath.exists()) {
                Log.i(TAG, targetFilePath + " is existent.");
            } else {
                FileOutputStream fileOutputStream = new FileOutputStream(targetPath);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }

                // 关闭流
                fileOutputStream.flush();
                fileOutputStream.close();

            }
            inputStream.close();
            String md5 = getFileMD5(targetPath);
            Log.i(TAG, "File:" + targetFilePath + "  md5:" + md5);
            return targetFilePath;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getFileExtension(String filePath) {
        if (isFileExists(filePath)) {
            File file = new File(filePath);
            String fileName = file.getName();
            if (fileName.lastIndexOf('.') > 0) {
                return fileName.substring(fileName.lastIndexOf('.') + 1);
            } else {
                return ""; // 如果没有扩展名，返回空字符串
            }
        } else {
            return "";
        }
    }

    public static String getAddressExtension(String address) {
        if (address.lastIndexOf('.') > 0) {
            return address.substring(address.lastIndexOf('.') + 1);
        } else {
            return "";
        }
    }

    public static boolean fixWavHeader(String wavPath) {
        if (isFileExists(wavPath)) {
            try {
                File wavFile = new File(wavPath);
                FileInputStream fis = new FileInputStream(wavFile);
                byte[] header = new byte[44]; // WAV头长度为44字节
                if (fis.read(header) != 44) {
                    Log.e(TAG, "WAV header is not valid!");
                    fis.close();
                    return false;
                }

                String riff = new String(header, 0, 4);
                String wave = new String(header, 8, 4);

                if (riff.equals("RIFF") && wave.equals("WAVE")) {
                } else {
                    Log.e(TAG, "WAV file is not valid!");
                    fis.close();
                    return false;
                }

                // 获取数据部分
                byte[] data = new byte[(int) (wavFile.length() - 44)];
                fis.read(data);
                fis.close();

                // 计算实际数据长度
                int actualDataLength = data.length;

                // 更新WAV头中的数据长度
                // 数据长度：从第40字节开始的4字节
                int dataSizePosition = 40;
                header[dataSizePosition] = (byte) (actualDataLength & 0xFF);
                header[dataSizePosition + 1] = (byte) ((actualDataLength >> 8) & 0xFF);
                header[dataSizePosition + 2] = (byte) ((actualDataLength >> 16) & 0xFF);
                header[dataSizePosition + 3] = (byte) ((actualDataLength >> 24) & 0xFF);

                // 写入修订后的WAV文件
                FileOutputStream fos = new FileOutputStream(wavFile);
                fos.write(header);
                fos.write(data);
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    public static String getDeviceIdFromFile(String accessFilePath, String defaultDeviceId) {
        if (isFileExists(accessFilePath)) {
        } else {
            Log.w(TAG, "new device id, use default " + defaultDeviceId);
            return defaultDeviceId;
        }

        try {
            String deviceId;
            File file = new File(accessFilePath);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();

            // 读取JSON内容并获取String和int对象
            String jsonContent = stringBuilder.toString();
            JSONObject jsonObject = JSON.parseObject(jsonContent);
            if (jsonObject.getString("device_id") != null &&
                    !jsonObject.getString("device_id").isEmpty()) {
                deviceId = jsonObject.getString("device_id");
                Log.i(TAG, "Get device id: " + deviceId);
                return deviceId;
            } else {
                Log.w(TAG, "cannot find device id, use default " + defaultDeviceId);
                return defaultDeviceId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultDeviceId;
    }

    public static boolean saveDeviceIdToFile(String accessFilePath, String deviceId) {
        if (isFileExists(accessFilePath)) {
        } else {
            return false;
        }

        try {
            File file = new File(accessFilePath);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();

            // 读取JSON内容并获取String和int对象
            String jsonContent = stringBuilder.toString();
            JSONObject jsonObject = JSON.parseObject(jsonContent);
            jsonObject.put("device_id", deviceId);

            FileWriter fileWriter = new FileWriter(accessFilePath);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(jsonObject.toString());
            Log.i(TAG, "Save " + jsonObject.toString() + "into " + accessFilePath);
            bufferedWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static List<String> getVoiceList(String voiceType) {
        List<String> Font = new ArrayList<String>();
        if (voiceType == "SambertTts") { /* 百炼大模型Sambert单向流式语音合成 */
            Font = Arrays.asList(
                    "sambert-zhinan-v1;知楠;广告男声;中英文;48000",
                    "sambert-zhiqi-v1;知琪;温柔女声;中英文;48000",
                    "sambert-zhichu-v1;知厨;舌尖男声;中英文;48000",
                    "sambert-zhide-v1;知德;新闻男声;中英文;48000",
                    "sambert-zhijia-v1;知佳;标准女声;中英文;48000",
                    "sambert-zhiru-v1;知茹;新闻女声;中英文;48000",
                    "sambert-zhiqian-v1;知倩;资讯女声;中英文;48000",
                    "sambert-zhixiang-v1;知祥;磁性男声;中英文;48000",
                    "sambert-zhiwei-v1;知薇;萝莉女声;中英文;48000",
                    "sambert-zhihao-v1;知浩;咨询男声;中英文;16000",
                    "sambert-zhijing-v1;知婧;严厉女声;中英文;16000",
                    "sambert-zhiming-v1;知茗;诙谐男声;中英文;16000",
                    "sambert-zhimo-v1;知墨;情感男声;中英文;16000",
                    "sambert-zhina-v1;知娜;浙普女声;中英文;16000",
                    "sambert-zhishu-v1;知树;资讯男声;中英文;16000",
                    "sambert-zhistella-v1;知莎;知性女声;中英文;16000",
                    "sambert-zhiting-v1;知婷;电台女声;中英文;16000",
                    "sambert-zhixiao-v1;知笑;资讯女声;中英文;16000",
                    "sambert-zhiya-v1;知雅;严厉女声;中英文;16000",
                    "sambert-zhiye-v1;知晔;青年男声;中英文;16000",
                    "sambert-zhiying-v1;知颖;软萌童声;中英文;16000",
                    "sambert-zhiyuan-v1;知媛;知心姐姐;中英文;16000",
                    "sambert-zhiyue-v1;知悦;温柔女声;中英文;16000",
                    "sambert-zhigui-v1;知柜;直播女声;中英文;16000",
                    "sambert-zhishuo-v1;知硕;自然男声;中英文;16000",
                    "sambert-zhimiao-emo-v1;知妙(多情感);阅读产品简介数字人直播;中英文;16000",
                    "sambert-zhimao-v1;知猫;直播女声;中英文;16000",
                    "sambert-zhilun-v1;知伦;悬疑解说;中英文;16000",
                    "sambert-zhifei-v1;知飞;激昂解说;中英文;16000",
                    "sambert-zhida-v1;知达;标准男声;中英文;16000",
                    "sambert-camila-v1;Camila;西班牙语女声;西班牙语;16000",
                    "sambert-perla-v1;Perla;意大利语女声;意大利语;16000",
                    "sambert-indah-v1;Indah;印尼语女声;印尼语;16000",
                    "sambert-clara-v1;Clara;法语女声;法语;16000",
                    "sambert-hanna-v1;Hanna;德语女声;德语;16000",
                    "sambert-beth-v1;Beth;咨询女声;美式英文;16000",
                    "sambert-betty-v1;Betty;客服女声;美式英文;16000",
                    "sambert-cally-v1;Cally;自然女声;美式英文;16000",
                    "sambert-cindy-v1;Cindy;对话女声;美式英文;16000",
                    "sambert-eva-v1;Eva;陪伴女声;美式英文;16000",
                    "sambert-donna-v1;Donna;教育女声;美式英文;16000",
                    "sambert-brian-v1;Brian;客服男声;美式英文;16000",
                    "sambert-waan-v1;Waan;泰语女声;泰语;16000",
                    "<更多音色请查看官网列表>"
            );
        } else if (voiceType == "CosyVoiceV3" || voiceType == "cosyvoice-v3") { /* 百炼大模型Cosyvoice双向流式语音合成 */
            Font = Arrays.asList(
                    /* 童声（标杆音色） */
                    "longhuohuo_v3-龙火火;桀骜不驯男童;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longhuhu_v3-龙呼呼;天真烂漫女童;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 方言（标杆音色） */
                    "longchuanshu_v3-龙川叔;油腻搞笑叔;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "<更多音色请查看官网列表>"
            );
        } else if (voiceType == "cosyvoice-v3-plus") { /* 百炼大模型Cosyvoice双向流式语音合成 */
            Font = Arrays.asList(
                    "<目前只能使用克隆音色, 详细请见官网说明>"
            );
        } else if (voiceType == "CosyVoiceV2" || voiceType == "cosyvoice-v2") { /* 百炼大模型Cosyvoice双向流式语音合成 */
            Font = Arrays.asList(
                    /* 语音助手 */
                    "longyumi_v2-YUMI;正经青年女;中英文",
                    "longxiaochun_v2-龙小淳;知性积极女;中英文",
                    "longxiaoxia_v2-龙小夏;沉稳权威女;中英文",
                    /* 童声（标杆音色） */
                    "longhuohuo-龙火火;桀骜不驯男童;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longhuhu-龙呼呼;天真烂漫女童;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 方言（标杆音色） */
                    "longchuanshu-龙川叔;油腻搞笑叔;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 消费电子-教育培训 */
                    "longanpei-龙安培;青少年教师女;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 消费电子-儿童陪伴 */
                    "longwangwan-龙汪汪;台湾少年音;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longpaopao-龙泡泡;飞天泡泡音;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 消费电子-儿童有声书 */
                    "longshanshan-龙闪闪;戏剧化童声;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longniuniu-龙牛牛;阳光男童声;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 短视频配音 */
                    "longdaiyu-龙黛玉;娇率才女音;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longgaoseng-龙高僧;得道高僧音;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 客服 */
                    "longyingmu-龙应沐;优雅知性女;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longyingxun-龙应询;年轻青涩男;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longyingcui-龙应催;严肃催收男;中英文",
                    "longyingda-龙应答;开朗高音女;中英文",
                    "longyingjing-龙应静;低调冷静女;中英文",
                    "longyingyan-龙应严;义正严辞女;中英文",
                    "longyingtian-龙应甜;温柔甜美女;中英文",
                    "longyingbing-龙应冰;尖锐强势女;中英文",
                    "longyingtao-龙应桃;温柔淡定女;中英文",
                    "longyingling-龙应聆;温和共情女;中英文",
                    /* 直播带货 */
                    "longanran-龙安燃;活泼质感女;中英文",
                    "longanxuan-龙安宣;经典直播女;中英文",
                    "longanchong-龙安冲;激情推销男;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longanping-龙安萍;高亢直播女;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    /* 有声书 */
                    "longbaizhi-龙白芷;睿气旁白女;中英文", /*若有语音服务业务对接人，请直接联系其申请开通；否则请提交工单申请*/
                    "longsanshu-龙三叔;沉稳质感男;中英文",
                    "longxiu_v2-龙修;博才说书男;中英文",
                    "longmiao_v2-龙妙;抑扬顿挫女;中英文",
                    "longyue_v2-龙悦;温暖磁性女;中英文",
                    "longnan_v2-龙楠;睿智青年男;中英文",
                    "longyuan_v2-龙媛;温暖治愈女;中英文",
                    /* 社交陪伴 */
                    "longanrou-龙安柔;温柔闺蜜女;中英文",
                    "longqiang_v2-龙嫱;浪漫风情女;中英文",
                    "longhan_v2-龙寒;温暖痴情男;中英文",
                    "longxing_v2-龙星;温婉邻家女;中英文",
                    "longhua_v2-龙华;元气甜美女;中英文",
                    "longwan_v2-龙婉;积极知性女;中英文",
                    "longcheng_v2-龙橙;智慧青年男;中英文",
                    "longfeifei_v2-龙菲菲;甜美娇气女;中英文",
                    "longxiaocheng_v2-龙小诚;磁性低音男;中英文",
                    "longzhe_v2-龙哲;呆板大暖男;中英文",
                    "longyan_v2-龙颜;温暖春风女;中英文",
                    "longtian_v2-龙天;磁性理智男;中英文",
                    "longze_v2-龙泽;温暖元气男;中英文",
                    "longshao_v2-龙邵;积极向上男;中英文",
                    "longhao_v2-龙浩;多情忧郁男;中英文",
                    "kabuleshen_v2-龙深;实力歌手男;中英文",
                    /* 童声 */
                    "longjielidou_v2-龙杰力豆;阳光顽皮男;中英文",
                    "longling_v2-龙铃;稚气呆板女;中英文",
                    "longke_v2-龙可;懵懂乖乖女;中英文",
                    "longxian_v2-龙仙;豪放可爱女;中英文",
                    /* 方言 */
                    "longlaotie_v2-龙老铁;东北直率男;中英文",
                    "longjiayi_v2-龙嘉怡;知性粤语女;中英文",
                    "longtao_v2-龙桃;积极粤语女;中英文",
                    /* 诗词朗诵 */
                    "longfei_v2-龙飞;热血磁性男;中英文",
                    "libai_v2-李白;古代诗仙男;中英文",
                    "longjin_v2-龙津;优雅温润男;中英文",
                    /* 新闻播报 */
                    "longshu_v2-龙书;沉稳青年男;中英文",
                    "loongbella_v2-Bella2.0;精准干练女;中英文",
                    "longshuo_v2-龙硕;博才干练男;中英文",
                    "longxiaobai_v2-龙小白;沉稳播报女;中英文",
                    "longjing_v2-龙婧;典型播音女;中英文",
                    "loongstella_v2-loongstella;飒爽利落女;中英文",
                    /* 出海营销 */
                    "loongeva_v2-loongeva;知性英文女;英文",
                    "loongbrian_v2-loongbrian;沉稳英文男;英文",
                    "loongabby_v2-loongabby;美式英文女;英文",
                    "loongkyong_v2-loongkyong;韩语女;韩语",
                    "loongtomoka_v2-loongtomoka;日语女;日语",
                    "loongtomoya_v2-loongtomoya;日语男;日语",
                    "<更多音色请查看官网列表>"
            );
        } else if (voiceType == "CosyVoiceV1" || voiceType == "cosyvoice-v1") { /* 百炼大模型Cosyvoice双向流式语音合成 */
            Font = Arrays.asList(
                    "longwan-龙婉;聊天数字人;中文普通话",
                    "longcheng-龙橙;聊天数字人;中文普通话",
                    "longhua-龙华;聊天数字人;中文普通话",
                    "longxiaochun-龙小淳;聊天数字人;中英文",
                    "longxiaoxia-龙小夏;聊天数字人;中文普通话",
                    "longxiaocheng-龙小诚;聊天数字人;中英文",
                    "longxiaobai-龙小白;聊天数字人;中文普通话",
                    "longlaotie-龙老铁;新闻播报;东北口音",
                    "longshu-龙书;智能客服;中文普通话",
                    "longshuo-龙硕;语音助手;中文普通话",
                    "longjing-龙婧;语音助手;中文普通话",
                    "longmiao-龙妙;语音助手;中文普通话",
                    "longyue-龙悦;语音助手;中文普通话",
                    "longyuan-龙媛;聊天数字人;中文普通话",
                    "longfei-龙飞;有声书;中文普通话",
                    "longjielidou-龙杰力豆;聊天助手;中文普通话+英文",
                    "longtong-龙彤;聊天数字人;中文普通话",
                    "longxiang-龙祥;新闻播报;中文普通话",
                    "loongstella-Stella;语音助手;中文普通话+英文",
                    "loongbella-Bella;语音助手;中文普通话",
                    "<更多音色请查看官网列表>"
            );
        } else if (voiceType == "StreamInputTts") { /* NLS 流式文本语音合成（双向流式） */
            Font = Arrays.asList(
                    "longcheng_v2-龙橙;阳光男声;中英文", "longhua_v2-龙华;活泼女童;中英文",
                    "abin-阿斌;广东普通话;中英文", "zhixiaobai-知小白;普通话女声;中英文",
                    "zhixiaoxia-知小夏;普通话女声;中英文", "zhixiaomei-知小妹;普通话女声;中英文",
                    "zhigui-知柜;普通话女声;中英文", "zhishuo-知硕;普通话男声;中英文",
                    "aixia-艾夏;普通话女声;中英文", "cally-Cally;美式英文女声;英文",
                    "zhifeng_emo-知锋_多情感;多种情感男声;中英文", "zhibing_emo-知冰_多情感;多种情感男声;中英文",
                    "ninger-宁儿;标准女声;中文", "ruilin-瑞琳;标准女声;中文", "aina-艾娜;浙普女声;中文",
                    "yina-伊娜;浙普女声;中文", "sitong-思彤;儿童音;中文", "xiaobei-小北;萝莉女声;中文",
                    "harry-Harry;英音男声;英文", "abby-Abby;美音女声;英文", "shanshan-姗姗;粤语女声;粤英文",
                    "chuangirl-小玥;四川话女声;中英文", "qingqing-青青;中国台湾话女声;中文",
                    "cuijie-翠姐;东北话女声;中文", "xiaoze-小泽;湖南重口音男声;中文", "tomoka-智香;日语女声;日文",
                    "tomoya-智也;日语男声;日文", "indah-Indah;印尼语女声;印尼语",
                    "farah-Farah;马来语女声;马来语", "tala-Tala;菲律宾语女声;菲律宾语",
                    "tien-Tien;越南语女声;越南语", "Kyong-Kyong;韩语女声;韩语", "masha-masha;俄语女声;俄语",
                    "camila-camila;西班牙语女声;西班牙语", "perla-perla;意大利语女声;意大利语",
                    "kelly-Kelly;香港粤语女声;香港粤语", "clara-clara;法语女声;法语",
                    "hanna-hanna;德语女声;德语", "waan-waan;泰语女声;泰语",
                    "eva_ecmix-eva_ecmix;美式英文女声;英中文",
                    "longchen-龙臣;译制片男声;中英文", "longxiong-龙熊;译制片男声;中英文",
                    "longyu-龙玉;御姐女声;中英文", "longjiao-龙娇;御姐女声;中英文",
                    "longmei-龙玫;温柔女声;中英文", "longgui-龙瑰;温柔女声;中英文",
                    "longping-龙乒;体育解说男声;中英文", "longpang-龙乓;体育解说男声;中英文",
                    "longwu-龙无;无厘头男声;中英文", "longqi-龙奇;活泼童声;中英文",
                    "longxian_normal-龙仙;阳光女声;中英文", "longfeifei-龙菲;成熟女声;中英文",
                    "longxiu-龙修;青年男声;中英文", "longdachui-龙大锤;幽默男声;中英文",
                    "longjiajia-龙佳佳;亲和女声;中英文", "longjiayi-龙嘉怡;粤语女声;中英文",
                    "longtao-龙桃;粤语女声;中英文", "longjiaxin-龙嘉欣;粤语女声;中英文",
                    "longcheng-龙橙;阳光男声;中英文", "longzhe-龙哲;成熟男声;中英文",
                    "longnan-龙楠;青年男声;中英文", "longyan-龙颜;亲切女声;中英文",
                    "longqiang-龙嫱;慵懒女声;中英文", "longhua-龙华;活泼女童;中英文",
                    "longxing-龙星;暖心女声;中英文", "longjin-龙津;青年男声;中英文",
                    "longhan-龙寒;青年男声;中英文", "longtian-龙天;霸总男声;中英文",
                    "longshuo-龙硕;沉稳男声;中英文", "loongstella-Stella2.0;飒爽女声;中英文",
                    "longxiaocheng-龙小诚;气质大叔;中英文", "longxiaoxia-龙小夏;温柔女声;中英文",
                    "longxiaochun-龙小淳;温柔姐姐;中英文", "longxiaobai-龙小白;闲聊女声;中英文",
                    "longlaotie-龙老铁;东北男声;中英文", "longyue-龙悦;评书女声;中英文",
                    "loongbella-Bella2.0;新闻女声;中英文", "longshu-龙书;新闻男声;中英文",
                    "longjing-龙婧;严肃女声;中英文", "longmiao-龙妙;气质女声;中英文",
                    "libai-龙老李;普通话男声;中英文", "longwan-龙婉;普通话女声;中英文",
                    "longke-龙可;活泼女童;中英文", "longling-龙铃;活泼女童;中英文",
                    "longshao-龙绍;活力男声;中英文", "longze-龙泽;阳光男声;中英文",
                    "longhao-龙浩;温暖男声;中英文"
            );
//        } else if (voiceType == "NlsTts") { /* NLS 语音合成（单向流式） */
//            Font =  Arrays.asList(
//                    "aiqi-艾琪;温柔女声;中英文", "aijia-艾佳;标准女声;中英文", "aicheng-艾诚;标准男声;中英文",
//                    "aida-艾达;标准男声;中英文", "aiya-艾雅;严厉女声;中英文", "aixia-艾夏;亲和女声;中英文",
//                    "aimei-艾美;甜美女声;中英文", "aiyu-艾雨;自然女声;中英文", "aiyue-艾悦;温柔女声;中英文",
//                    "aijing-艾婧;严厉女声;中英文", "aina-艾娜;浙普女声;中文", "aitong-艾彤;儿童音;中文",
//                    "aiwei-艾薇;萝莉女声;中文", "aibao-艾宝;萝莉女声;中文", "abby-Abby;美音女声;英文",
//                    "andy-Andy;美音男声;英文", "aifei-艾飞;激昂解说;中文", "ava-ava;美语女声;英文",
//                    "ailun-艾伦;悬疑解说;中英文", "aishuo-艾硕;自然男声;中英文", "annie-Annie;美语女声;英文",
//                    "aikan-艾侃;天津话男声;中文",
//                    "becca-Becca;美语客服女声;英文",
//                    "cuijie-翠姐;东北话女声;中文", "chuangirl-小玥;四川话女声;中文",
//                    "dahu-大虎;东北话男声;中文",
//                    "eric-Eric;英音男声;英文", "emily-Emily;英音女声;英文",
//                    "farah-Farah;马来语女声;马来语",
//                    "harry-Harry;英音男声;英文",
//                    "indah-Indah;印尼语女声;印尼语",
//                    "jiajia-佳佳;粤语女声;粤英", "jielidou-杰力豆;治愈童声;中文",
//                    "kenny-Kenny;沉稳男声;中英文", "Kyong-Kyong;韩语女声;韩语",
//                    "luna-Luna;英音女声;英文", "luca-Luca;英音男声;英文", "lydia-Lydia;英中双语女声;中英文",
//                    "laotie-老铁;东北老铁;中文", "laomei-老妹;吆喝女声;中文",
//                    "maoxiaomei-猫小美;活力女声;中英文", "mashu-马树;儿童剧男声;中英文",
//                    "masha-masha;俄语女声;俄语",
//                    "ninger-宁儿;标准女声;中文",
//                    "olivia-Olivia;英音女声;英文",
//                    "qingqing-青青;中国台湾话女声;中文", "guijie-柜姐;亲切女声;中英文",
//                    "qiaowei-巧薇;卖场广播;中英文",
//                    "rosa-Rosa;自然女声;中英文", "ruilin-瑞琳;标准女声;中文", "ruoxi-若兮;温柔女声;中英文",
//                    "siqi-思琪;温柔女声;中英文", "sijia-思佳;标准女声;中英文", "sicheng-思诚;标准男声;中英文",
//                    "siyue-思悦;温柔女声;中英文", "sijing-思婧;严厉女声;中文", "sitong-思彤;儿童音;中文",
//                    "shanshan-姗姗;粤语女声;粤语", "stella-Stella;知性女声;中英文",
//                    "stanley-Stanley;沉稳男声;中英文",
//                    "tomoka-智香;日语女声;日语", "tomoya-智也;日语男声;日语", "taozi-桃子;粤语女声;粤语",
//                    "tala-Tala;菲律宾语女声;菲律宾语", "tien-Tien;越南语女声;越南语",
//                    "wendy-Wendy;英音女声;英文", "william-William;英音男声;英文",
//                    "xiaomei-小美;甜美女声;中英文", "xiaobei-小北;萝莉女声;中文",
//                    "xiaoze-小泽;湖南重口音男声;中文", "xiaoxian-小仙;亲切女声;中英文",
//                    "xiaoyun-小云;标准女声;中英文", "xiaogang-小刚;标准男声;中英文",
//                    "yina-伊娜;浙普女声;中文", "yuer-悦儿;儿童剧女声;中文", "yaqun-亚群;卖场广播;中英文",
//                    "zhimiao_emo-知妙_多情感;多种情感女声;中英文", "zhimi_emo-知米_多情感;多种情感女声;中英文",
//                    "zhiyan_emo-知燕_多情感;多种情感女声;中英文", "zhibei_emo-知贝_多情感;多种情感女声;中英文",
//                    "zhitian_emo-知甜_多情感;多种情感女声;中英文"
//            );
        } else if (voiceType == "NlsTts" || voiceType == "LongNlsTts" || voiceType == "pre-sambert-gpu-test") {
            /* NLS 语音合成（单向流式） */
            /* NLS 长文本语音合成（单向流式） */
            /* 百炼大模型Sambert双向流式语音合成 */
            Font =  Arrays.asList(
                    "zhixiaobai-知小白;普通话女声;中英文",
                    "zhixiaoxia-知小夏;普通话女声;中英文",
                    "zhixiaomei-知小妹;普通话女声;中英文",
                    "zhigui-知柜;普通话女声;中英文",
                    "zhishuo-知硕;普通话男声;中英文",
                    "aixia-艾夏;普通话女声;中英文",
                    "cally-Cally;美式英文女声;英文",
                    "zhifeng_emo-知锋_多情感;多种情感男声;中英文",
                    "zhibing_emo-知冰_多情感;多种情感男声;纯中文",
                    "zhimiao_emo-知妙_多情感;多种情感女声;中英",
                    "zhimi_emo-知米_多情感;多种情感女声;中英文",
                    "zhiyan_emo-知燕_多情感;多种情感女声;中英文",
                    "zhibei_emo-知贝_多情感;多种情感童声;中英文",
                    "zhitian_emo-知甜_多情感;多种情感女声;中英文",
                    "xiaoyun-小云;标准女声;中英文",
                    "xiaogang-小刚;标准男声;中英文",
                    "ruoxi-若兮;温柔女声;中英文",
                    "siqi-思琪;温柔女声;中英文",
                    "sijia-思佳;标准女声;中英文",
                    "sicheng-思诚;标准男声;中英文",
                    "aiqi-艾琪;温柔女声;中英文",
                    "aijia-艾佳;标准女声;中英文",
                    "aicheng-艾诚;标准男声;中英文",
                    "aida-艾达;标准男声;中英文",
                    "ninger-宁儿;标准女声;纯中文",
                    "ruilin-瑞琳;标准女声;纯中文",
                    "siyue-思悦;温柔女声;中英文",
                    "aiya-艾雅;严厉女声;中英文",
                    "aimei-艾美;甜美女声;中英文",
                    "aiyu-艾雨;自然女声;中英文",
                    "aiyue-艾悦;温柔女声;中英文",
                    "aijing-艾婧;严厉女声;中英文",
                    "xiaomei-小美;甜美女声;中英文",
                    "aina-艾娜;浙普女声;纯中文",
                    "yina-伊娜;浙普女声;纯中文",
                    "sijing-思婧;严厉女声;纯中文",
                    "sitong-思彤;儿童音;纯中文",
                    "xiaobei-小北;萝莉女声;纯中文",
                    "aitong-艾彤;儿童音;纯中文",
                    "aiwei-艾薇;萝莉女声;纯中文",
                    "aibao-艾宝;萝莉女声;纯中文",
                    "harry-Harry;英音男声;英文",
                    "abby-Abby;美音女声;英文",
                    "andy-Andy;美音男声;英文",
                    "eric-Eric;英音男声;英文",
                    "emily-Emily;英音女声;英文",
                    "luna-Luna;英音女声;英文",
                    "luca-Luca;英音男声;英文",
                    "wendy-Wendy;英音女声;英文",
                    "william-William;英音男声;英文",
                    "olivia-Olivia;英音女声;英文",
                    "shanshan-姗姗;粤语女声;粤英",
                    "aiyuan-艾媛;知心姐姐;中英文",
                    "aiying-艾颖;软萌童声;中英文",
                    "aixiang-艾祥;磁性男声;中英文",
                    "aimo-艾墨;情感男声;中英文",
                    "aiye-艾晔;青年男声;中英文",
                    "aiting-艾婷;电台女声;中英文",
                    "aifan-艾凡;情感女声;中英文",
                    "lydia-Lydia;英中双语女声;英中",
                    "chuangirl-小玥;四川话女声;中英文",
                    "aishuo-艾硕;自然男声;中英文",
                    "qingqing-青青;中国台湾话女声;纯中文",
                    "cuijie-翠姐;东北话女声;纯中文",
                    "xiaoze-小泽;湖南重口音男声;纯中文",
                    "ainan-艾楠;广告男声;中英文",
                    "aihao-艾浩;资讯男声;中英文",
                    "aiming-艾茗;诙谐男声;中英文",
                    "aixiao-艾笑;资讯女声;中英文",
                    "aichu-艾厨;舌尖男声;中英文",
                    "aiqian-艾倩;资讯女声;中英文",
                    "tomoka-智香;日语女声;日语",
                    "tomoya-智也;日语男声;日语",
                    "annie-Annie;美语女声;英文",
                    "aishu-艾树;资讯男声;中英文",
                    "airu-艾茹;新闻女声;中英文",
                    "jiajia-佳佳;粤语女声;粤英",
                    "indah-Indah;印尼语女声;印尼语",
                    "taozi-桃子;粤语女声;粤英",
                    "guijie-柜姐;亲切女声;中英文",
                    "stella-Stella;知性女声;中英文",
                    "stanley-Stanley;沉稳男声;中英文",
                    "kenny-Kenny;沉稳男声;中英文",
                    "rosa-Rosa;自然女声;中英文",
                    "farah-Farah;马来语女声;马来语",
                    "mashu-马树;儿童剧男声;通用",
                    "zhiqi-知琪;温柔女声;中英文",
                    "zhichu-知厨;舌尖男声;中英文",
                    "xiaoxian-小仙;亲切女声;中英文",
                    "maoxiaomei-猫小美;活力女声;中英文",
                    "zhixiang-知祥;磁性男声;中英文",
                    "zhijia-知佳;标准女声;中英文",
                    "zhinan-知楠;广告男声;中英文",
                    "zhiqian-知倩;资讯女声;中英文",
                    "zhiru-知茹;新闻女声;中英文",
                    "zhide-知德;新闻男声;中英文",
                    "zhifei-知飞;激昂解说;中英文",
                    "aifei-艾飞;激昂解说;中英文",
                    "yaqun-亚群;卖场广播;中英文",
                    "qiaowei-巧薇;卖场广播;中英文",
                    "dahu-大虎;东北话男声;中英文",
                    "ava-ava;美语女生;英文",
                    "zhilun-知伦;悬疑解说;中英文",
                    "ailun-艾伦;悬疑解说;中英文",
                    "jielidou-杰力豆;治愈童声;纯中文",
                    "zhiwei-知薇;萝莉女声;纯中文",
                    "laotie-老铁;东北老铁;纯中文",
                    "laomei-老妹;吆喝女声;纯中文",
                    "aikan-艾侃;天津话男声;纯中文",
                    "tala-Tala;菲律宾语女声;菲语",
                    "zhitian-知甜;甜美女声;中英文",
                    "zhiqing-知青;中国台湾话女生;纯中文",
                    "tien-Tien;越南语女声;越南语",
                    "becca-Becca;美语客服女声;英文",
                    "Kyong-Kyong;韩语女声;韩语",
                    "masha-masha;俄语女声;俄语",
                    "camila-camila;西班牙语女声;西语",
                    "perla-perla;意大利语女声;意语",
                    "zhimao-知猫;普通话女声;中文",
                    "zhiyuan-知媛;普通话女声;中文",
                    "zhiya-知雅;普通话女声;中文",
                    "zhiyue-知悦;普通话女声;中文",
                    "zhida-知达;普通话男声;中英文",
                    "zhistella-知莎;普通话女声;中文",
                    "kelly-Kelly;香港粤语女声;粤语",
                    "clara-clara;法语女声;法语",
                    "hanna-hanna;德语女声;德语",
                    "waan-waan;泰语女声;泰语",
                    "betty-betty;美式英文女声;英文",
                    "beth-beth;美式英文女声;英文",
                    "cindy-cindy;美式英文女声;英文",
                    "donna-donna;美式英文女声;英文",
                    "eva-eva;美式英文女声;英文",
                    "brian-brian;美式英文男声;英文",
                    "david-david;美式英文男声;英文",
                    "abby_ecmix-abby_ecmix;美式英文女声;中英文",
                    "annie_ecmix-annie_ecmix;美式英文女声;中英文",
                    "andy_ecmix-andy_ecmix;美式英文男声;中英文",
                    "ava_ecmix-ava_ecmix;美式英文女声;中英文",
                    "betty_ecmix-betty_ecmix;美式英文女声;中英文",
                    "beth_ecmix-beth_ecmix;美式英文女声;中英文",
                    "brian_ecmix-brian_ecmix;美式英文男声;中英文",
                    "cindy_ecmix-cindy_ecmix;美式英文女声;中英文",
                    "cally_ecmix-cally_ecmix;美式英文女声;中英文",
                    "donna_ecmix-donna_ecmix;美式英文女声;中英文",
                    "david_ecmix-david_ecmix;美式英文男声;中英文",
                    "eva_ecmix-eva_ecmix;美式英文女声;中英文"
            );
        }
        return Font;
    }
}
