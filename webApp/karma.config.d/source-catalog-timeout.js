// The catalog DOM test exercises every provider through persistence, preparation and copy.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 30000 });
