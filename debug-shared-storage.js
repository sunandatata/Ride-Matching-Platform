// Debug Shared Storage Between Apps
const puppeteer = require('puppeteer');

async function debugSharedStorage() {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
    const riderPage = await browser.newPage();
    const driverPage = await browser.newPage();

    console.log('='.repeat(60));
    console.log('DEBUG: Shared Storage Test');
    console.log('='.repeat(60));

    // Login to both apps
    console.log('\n1. Logging in...');
    await riderPage.goto('http://localhost:5173', { waitUntil: 'networkidle2', timeout: 10000 });
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });
    await riderPage.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Rider logged in');

    await driverPage.goto('http://localhost:3002', { waitUntil: 'networkidle2', timeout: 10000 });
    await driverPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });
    await driverPage.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Driver logged in');

    // Check storage before request
    console.log('\n2. Checking shared storage BEFORE ride request...');
    let sharedBefore = await riderPage.evaluate(() => {
      return {
        key: 'shared:available_rides',
        value: localStorage.getItem('shared:available_rides'),
        parsed: localStorage.getItem('shared:available_rides') ? JSON.parse(localStorage.getItem('shared:available_rides')) : null
      };
    });
    console.log(`  Value: ${sharedBefore.value}`);
    console.log(`  Parsed: ${JSON.stringify(sharedBefore.parsed)}`);

    // Request ride
    console.log('\n3. Requesting ride...');
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const requestBtn = buttons.find(btn => btn.textContent.includes('Request Ride'));
      if (requestBtn) requestBtn.click();
    });
    await new Promise(resolve => setTimeout(resolve, 500));

    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const confirmBtn = buttons.find(btn => btn.textContent.includes('Confirm Ride'));
      if (confirmBtn) confirmBtn.click();
    });
    console.log('✓ Ride requested');

    // Check storage after request
    await new Promise(resolve => setTimeout(resolve, 1000));
    console.log('\n4. Checking shared storage AFTER ride request...');
    let sharedAfter = await riderPage.evaluate(() => {
      const rides = localStorage.getItem('shared:available_rides');
      return {
        key: 'shared:available_rides',
        value: rides,
        parsed: rides ? JSON.parse(rides) : null,
        count: rides ? JSON.parse(rides).length : 0
      };
    });
    console.log(`  Value: ${sharedAfter.value}`);
    console.log(`  Count: ${sharedAfter.count}`);
    if (sharedAfter.parsed) {
      console.log(`  Rides: ${JSON.stringify(sharedAfter.parsed, null, 2)}`);
    }

    // Check if driver can access the same storage
    console.log('\n5. Checking if driver app can access shared storage...');
    let driverStorage = await driverPage.evaluate(() => {
      const rides = localStorage.getItem('shared:available_rides');
      return {
        value: rides,
        parsed: rides ? JSON.parse(rides) : null,
        count: rides ? JSON.parse(rides).length : 0
      };
    });
    console.log(`  Driver sees: ${driverStorage.count} rides`);
    if (driverStorage.parsed) {
      console.log(`  Rides: ${JSON.stringify(driverStorage.parsed)}`);
    }

    console.log('\n' + '='.repeat(60));
    if (sharedAfter.count > 0 && driverStorage.count > 0) {
      console.log('✓ SHARED STORAGE WORKING');
    } else {
      console.log('⚠️  SHARED STORAGE NOT WORKING');
      console.log(`\nRider app saved: ${sharedAfter.count} rides`);
      console.log(`Driver app sees: ${driverStorage.count} rides`);
    }

    return { success: sharedAfter.count > 0 && driverStorage.count > 0 };
  } catch (error) {
    console.error(`❌ Error: ${error.message}`);
    return { success: false, error: error.message };
  } finally {
    if (browser) await browser.close();
  }
}

debugSharedStorage().catch(console.error);
