CREATE TABLE IF NOT EXISTS `{receivers}` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `receiver` CHAR(129) NOT NULL UNIQUE,
  PRIMARY KEY(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `{sharedKeys}` (
  `pk_id` INT NOT NULL,
  `hash` CHAR(40) NOT NULL,
  `sharedKey` TINYBLOB NOT NULL,
  PRIMARY KEY(`pk_id`, `hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
