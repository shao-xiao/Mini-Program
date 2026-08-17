ALTER TABLE notice
  ADD COLUMN show_on_home TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在小程序首页园区公告轮播展示' AFTER published_at;

CREATE INDEX idx_notice_home_carousel
  ON notice (show_on_home, publish_status, published_at);
