const env = {
  dev: {
    baseURL: 'http://localhost:8080/api'
  },
  prod: {
    baseURL: 'https://wuye.mingda.com.cn/api'
  }
}

const activeEnv = 'prod'

module.exports = {
  activeEnv,
  ...env[activeEnv]
}
