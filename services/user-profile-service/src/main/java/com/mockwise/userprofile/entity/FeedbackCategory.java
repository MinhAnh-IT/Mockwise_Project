package com.mockwise.userprofile.entity;

/**
 * What kind of feedback the user is sending. Lets the admin filter bug reports
 * (to fix) from feature requests and general impressions (for product/marketing
 * direction).
 */
public enum FeedbackCategory {
    BUG,
    FEATURE,
    GENERAL
}
