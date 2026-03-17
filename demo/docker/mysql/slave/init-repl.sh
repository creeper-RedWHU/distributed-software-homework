#!/bin/bash
# 配置从库复制 - 在MySQL初始化阶段执行
mysql -uroot -proot <<EOF
CHANGE MASTER TO
  MASTER_HOST='mysql-master',
  MASTER_PORT=3306,
  MASTER_USER='repl',
  MASTER_PASSWORD='repl_password',
  MASTER_AUTO_POSITION=1;
START SLAVE;
EOF
echo "Slave replication configured."
