# demo for gitlab-ci: java + mvn package + docker build + helm package

```
├── charts -- helm 的标准目录，用helm create 来创建
│   └── hello-world-ci-final
│       ├── Chart.yaml
│       ├── templates
│       │   ├── NOTES.txt
│       │   ├── _helpers.tpl
│       │   ├── deployment.yaml
│       │   ├── ingress.yaml
│       │   └── service.yaml
│       └── values.yaml
├── docker  -- 用来存放 dockerfile
│   └── dockerfile
├── hello-world-ci-final.iml
├── mvnw
├── mvnw.cmd
├── pom.xml
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── rbx
│   │   │           └── helloworldcifinal
│   │   │               ├── HelloWorldCiFinalApplication.java
│   │   │               ├── controller
│   │   │               │   ├── HealthController.java
│   │   │               │   └── HelloController.java
│   │   │               └── feignInterface
│   │   │                   └── FeignInterface.java
│   │   └── resources
│   │       ├── application.properties
│   │       ├── static
│   │       └── templates
│   └── test
│       └── java
│           └── com
│               └── rbx
│                   └── helloworldcifinal
│                       └── HelloWorldCiFinalApplicationTests.java
```
- 编写好java代码(pom 文件里 build 下 加 <finalName>app</finalName> 来保证，每次打包都是app.jar 这个文件)
- 编写dockerfile
```
FROM frolvlad/alpine-oraclejdk8:slim
VOLUME /tmp
# 前面pom文件强制命名的，后面会把jar包放到这个docker文件里，和dockerfile 保持在统一目录下
ADD app.jar app.jar 
ENV SPRING_APPLICATION_NAME="hello-world-ci-final"
ENV JAVA_OPTS="-Xmx128m -Xms128m"
EXPOSE 8080
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai  /etc/localtime
ENTRYPOINT [ "sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /app.jar --spring.application.name=${SPRING_APPLICATION_NAME} " ]

```

- 按照需求编写chart, 参照k8s/helm下的笔记
- 编写gitlab-ci.yml文件
```
image: 10.66.0.18:8888/55else/baseci:1.0.2

stages:
- mvn-package
- docker-build

maven-test-branches:
  tags:
  - java
  stage: mvn-package
  script:
  - git_merge master
  only:
  - /^bugfix-.*$/
  - /^feature-.*$/

maven-build:
  tags:
  - java
  stage: mvn-package

  #Store results in cache 
  #这里就是前面说的不用宿主机的文件映射，用artifacts来在不同的stage中传递文件，这里我们把target文件夹映射到target中，1个小时后删除，后面的stage中可以用target这个文件名来获取里面的文件
  #注意，其他stage要用这个artifacts，必须在job里声明dependency 这个job
#  artifacts:
#    name: "target"
#    expire_in: 1 hour
#    paths:
#    - target


  script:
  - update_pom_version
  - mvn package -U -DskipTests=false #打包
  - mkdir -p /root/cache #创建cache文件夹到宿主机
  #把生成的jar包放到宿主机，共享给下面的stage
  - cp target/app.jar /root/cache 
  

docker-build:
  tags:
  - java
  stage: docker-build
  script:
  - docker_build
  - chart_build
#  如果上一个stage不是用宿主机共享的方式，而是用artifacts来传递，那就要加下面的语句，来声明从哪个stage下载文件  
#  dependencies:
#  - maven-build
  only:
  - master
  - tags
  - develop
  - /^hotfix-.*$/
  - /^release-.*$/

#声明一个名叫auto_devops的脚本，供job使用
.auto_devops: &auto_devops |
                #rm -rf /root/.m2/settings.xml
                # echo -e "<?xml version=\""1.0\"" encoding=\""UTF-8\""?><settings xmlns=\""http://maven.apache.org/SETTINGS/1.0.0\"" xmlns:xsi=\""http://www.w3.org/2001/XMLSchema-instance\"" xsi:schemaLocation=\""http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\""><mirrors><mirror><id>alimaven</id><name>aliyun maven</name><url>http://maven.aliyun.com/nexus/content/groups/public/</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>" > /root/.m2/settings.xml

                export GROUP_NAME="55else"
                export PROJECT_NAME=${CI_PROJECT_NAME}
                export DOCKER_USER="admin"
                export DOCKER_PWD="Harbor12345"
                export DOCKER_REGISTRY="10.66.0.18:8888"


                COMMIT_TIMESTAMP=$(git log -1 --pretty=format:"%ci"| awk '{print $1$2}' | sed 's/[-:]//g') # git log -1 查看提交记录
                COMMIT_YEAR=${COMMIT_TIMESTAMP:0:4}
                COMMIT_MONTH=$(echo ${COMMIT_TIMESTAMP:4:2} | sed s'/^0//')
                COMMIT_DAY=$(echo ${COMMIT_TIMESTAMP:6:2} | sed s'/^0//')
                COMMIT_HOURS=${COMMIT_TIMESTAMP:8:2}
                COMMIT_MINUTES=${COMMIT_TIMESTAMP:10:2}
                COMMIT_SECONDS=${COMMIT_TIMESTAMP:12:2}
                export COMMIT_TIME=$COMMIT_YEAR.$COMMIT_MONTH.$COMMIT_DAY-$COMMIT_HOURS$COMMIT_MINUTES$COMMIT_SECONDS


                # 分支名

                #是否用 CIRCLECI
                if [ $CIRCLECI ]; then
                export BRANCH=$(echo $CIRCLE_BRANCH | tr '[A-Z]' '[a-z]' | tr '[:punct:]' '-')
                elif [ $GITLAB_CI ]; then
                export BRANCH=$CI_COMMIT_REF_SLUG
                fi

                # 默认Version
                if [ $CI_COMMIT_TAG ]; then
                export VERSION=$CI_COMMIT_TAG
                elif [ $CIRCLE_TAG ]; then
                export VERSION=$CIRCLE_TAG
                else
                export VERSION=$COMMIT_TIME-$BRANCH
                fi

                export FINAL_CI_COMMIT_TAG=$VERSION
                # 测试数据库，这个例子中用不到
                function database_test(){
                  while ! mysqlcheck --host=127.0.0.1 --user=root --password=${MYSQL_ROOT_PASSWORD} mysql; do sleep 1; done
                  echo "CREATE DATABASE hap_cloud_test DEFAULT CHARACTER SET utf8;" | \
                  mysql --user=root --password=${MYSQL_ROOT_PASSWORD} --host=127.0.0.1
                  java -Dspring.datasource.url="jdbc:mysql://127.0.0.1/hap_cloud_test?useUnicode=true&characterEncoding=utf-8&useSSL=false" \
                  -Dspring.datasource.username=root \
                  -Dspring.datasource.password=${MYSQL_ROOT_PASSWORD} \
                  -Ddata.dir=src/main/resources \
                  -jar /var/hapcloud/hap-liquibase-tool.jar
                  }
                #更新 pom 版本，把项目里pom文件里的version改成上面生成的version
                function update_pom_version(){
                  mvn versions:set -DnewVersion=${FINAL_CI_COMMIT_TAG} || \
                  find . -name pom.xml | xargs xml ed -L \
                  -N x=http://maven.apache.org/POM/4.0.0 \
                  -u '/x:project/x:version' -v "${FINAL_CI_COMMIT_TAG}"
                  mvn versions:commit
                }
                # 参数为要合并的远程分支名,默认develop
                # e.g. git_merge develop
                function git_merge(){
                  git config user.name ${GITLAB_USER_NAME}
                  git config user.email ${GITLAB_USER_EMAIL}
                  git checkout origin/${1:-"develop"}
                  git merge ${CI_COMMIT_SHA} --no-commit --no-ff
                }
                # 缓存jar包, 这里用不到
                function cache_jar(){
                  mkdir -p ${HOME}/.m2/${GROUP_NAME}/${CI_COMMIT_SHA}
                  cp target/app.jar ${HOME}/.m2/${GROUP_NAME}/${CI_COMMIT_SHA}/app.jar
                }
                # build helm chart
                function chart_build(){
                  #这里要注意，因为上面说过了，git runner启动的时候，虽然根据指定的docker images 加载，但是它对root文件夹下的文件会清理，所以/root/.helm 文件夹要从其他地方拿过来，不然helm命令会无效
                
                  cp -r /tmp/helm/.helm /root
                  CHART_PATH=`find . -maxdepth 3 -name Chart.yaml` # 获取 Chart.yaml 的路径 ：./charts/hello-world-ci-final/Chart.yaml
                  echo $CHART_PATH
                  #替换 chart 中 values.yaml 文件中 deployment 的 镜像仓库地址
                  sed -i 's/repository:.*$/repository\:\ '${DOCKER_REGISTRY}'\/'${GROUP_NAME}'\/'${PROJECT_NAME}'/g' ${CHART_PATH%/*}/values.yaml
                  helm package ${CHART_PATH%/*} --version ${FINAL_CI_COMMIT_TAG} --app-version ${FINAL_CI_COMMIT_TAG}
                  TEMP=${CHART_PATH%/*} # 截取最后/之前的路径 e.g: ./charts/hello-world-ci-final
                  FILE_NAME=${TEMP##*/} # 截取最后/之后的路径 e.g: hello-world-ci-final

                   #直接用helm 命令push
                   helm push /root/${FILE_NAME}/${FILE_NAME}-${FINAL_CI_COMMIT_TAG}.tgz 55else-repo
                  }

                # 清除缓存
                function clean_cache(){
                  rm -rf ${HOME}/.m2/${GROUP_NAME}/${CI_COMMIT_SHA}
                }

                # docker build
                function docker_build(){
                  # 将宿主机上的app.jar文件，放到项目的docker目录下
                  cp /root/cache/app.jar ${1:-"docker"}/app.jar || true
                  docker login -u ${DOCKER_USER} -p ${DOCKER_PWD} ${DOCKER_REGISTRY}
                  docker build --pull -t ${DOCKER_REGISTRY}/${GROUP_NAME}/${PROJECT_NAME}:${FINAL_CI_COMMIT_TAG} ${1:-"docker"}
                  docker push ${DOCKER_REGISTRY}/${GROUP_NAME}/${PROJECT_NAME}:${FINAL_CI_COMMIT_TAG}
                  # 删掉宿主机上的镜像，避免磁盘满了
                  docker rmi ${DOCKER_REGISTRY}/${GROUP_NAME}/${PROJECT_NAME}:${FINAL_CI_COMMIT_TAG}
                }

#在所有job执行前，都执行一个我们声明的一个叫auto_devops的脚本
before_script:
- *auto_devops


```

[baseci 参考：https://github.com/yezhoujie/java-docker-helm-baseci](https://github.com/yezhoujie/java-docker-helm-baseci)
