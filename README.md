# loginservice
**多系统统一登录服务**

抽取多个系统的登录模块为一个独立服务，为其他系统提供登录校验与注册服务。

- 采用技术
  - SpringBoot
  - Redis
  - MySql
 
 主要实现逻辑：
  用户注册/登录生成token(该token格式可以自定)，存储在Redis，在下次登录或进入系统前，通过cookie中拿到key，从redis中取出用户信息


