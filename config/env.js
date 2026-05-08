const env = {
  dev: {
    baseURL: 'http://localhost:8080/api'
  },
  prod: {
    baseURL: 'https://example.com/api'
  }
}

const activeEnv = 'dev'

module.exports = {
  activeEnv,
  ...env[activeEnv]
}
