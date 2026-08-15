package com.gojeom.storage;

/**
 * 업로드 용도. 스토리지 키의 최상위 경로가 된다. (ERD.md §8)
 *
 * <p>용도별로 경로를 나누면 삭제·수명 정책을 따로 걸 수 있고,
 * 소유권 검증도 경로 규칙만으로 가능해진다.
 */
public enum UploadPurpose {

    /** 내 사진. `profiles/{userId}/{uuid}.{ext}` */
    PROFILE_PHOTO("profiles"),

    /** 고점 참고 사진. 여러 장 첨부 가능. `references/{userId}/{uuid}.{ext}` */
    REFERENCE_IMAGE("references"),

    /** 인바디 서류 스캔본. `inbody/{userId}/{uuid}.{ext}` */
    INBODY_DOCUMENT("inbody");

    private final String prefix;

    UploadPurpose(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
