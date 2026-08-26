-- queue that keeps track of future assignments, that has not been published events on yet
CREATE TABLE future_assignment (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  start_date               DATE NULL,
  historic_assignment_id   BIGINT NOT NULL,
  
  CONSTRAINT fk_faq_hai FOREIGN KEY (historic_assignment_id) REFERENCES historic_assignment (id) ON DELETE CASCADE
);
