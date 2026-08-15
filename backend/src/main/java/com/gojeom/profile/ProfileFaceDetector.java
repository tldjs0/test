package com.gojeom.profile;

/** 프로필 사진에서 실제 얼굴 후보 수를 반환하는 서버 측 탐지 경계. */
public interface ProfileFaceDetector {

    int count(byte[] imageBytes);
}
