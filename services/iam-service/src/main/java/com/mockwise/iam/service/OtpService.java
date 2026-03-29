package com.mockwise.iam.service;

import com.mockwise.iam.common.config.OtpProperties;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.util.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OtpService {

    OtpProperties otpProperties;
    StringRedisTemplate redisTemplate;

    private static final int OTP_LENGTH = 6;

    public String generateOtpForVerifyAccount(String email) {
        String key = otpProperties.getKeyOtpVerifyAccount() + email;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new BusinessException(StatusCode.OTP_STILL_VALID);
        }

        return generateAndSaveOtp(key);
    }

    public String generateOtpForForgotPassword(String email) {
        String key = otpProperties.getKeyOtpForgotPass() + email;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new BusinessException(StatusCode.OTP_STILL_VALID);
        }

        return generateAndSaveOtp(key);
    }

    public void deleteOtp(String key) {
        redisTemplate.delete(key);
    }

    public String getVerifyAccountKey(String email) {
        return otpProperties.getKeyOtpVerifyAccount() + email;
    }

    public String getForgotPasswordKey(String email) {
        return otpProperties.getKeyOtpForgotPass() + email;
    }


    public boolean verifyOtp(String key, String otp) {
        String storedOtp = redisTemplate.opsForValue().get(key);
        return otp != null && otp.equals(storedOtp);
    }

    public boolean isOtpStillValid(String email) {
        String keyVerify = otpProperties.getKeyOtpVerifyAccount() + email;
        String keyForgot = otpProperties.getKeyOtpForgotPass() + email;

        return redisTemplate.hasKey(keyVerify)
                || redisTemplate.hasKey(keyForgot);
    }

    private String generateAndSaveOtp(String key) {
        String otp = generateRandomOtp();
        saveToRedis(key, otp);
        return otp;
    }

    private void saveToRedis(String key, String otp) {
        long expire = otpProperties.getTime_expire(); // seconds
        redisTemplate.opsForValue().set(key, otp, expire, TimeUnit.SECONDS);
    }

    private String generateRandomOtp() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
