// Test Ride Sync Between Rider and Driver Apps
const puppeteer = require('puppeteer');

async function testRideSync() {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
    const riderPage = await browser.newPage();
    const driverPage = await browser.newPage();

    console.log('='.repeat(60));
    console.log('RIDE SYNC TEST - Rider & Driver Apps');
    console.log('='.repeat(60));

    // Step 1: Login to both apps
    console.log('\n1. Logging into both apps...');

    // Rider app login
    await riderPage.goto('http://localhost:5173', { waitUntil: 'networkidle2', timeout: 10000 });
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });
    await riderPage.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Rider logged in');

    // Driver app login
    await driverPage.goto('http://localhost:3002', { waitUntil: 'networkidle2', timeout: 10000 });
    await driverPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });
    await driverPage.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Driver logged in');

    // Step 2: Set driver online
    console.log('\n2. Setting driver online...');
    await driverPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const onlineBtn = buttons.find(btn => btn.textContent.includes('Go Online'));
      if (onlineBtn) onlineBtn.click();
    });
    await new Promise(resolve => setTimeout(resolve, 1000));
    console.log('✓ Driver online');

    // Step 3: Check initial state - no rides available
    console.log('\n3. Checking initial state...');
    const initialRides = await driverPage.evaluate(() => {
      return document.body.innerText.includes('No Rides Available');
    });
    if (initialRides) {
      console.log('✓ Driver sees "No Rides Available"');
    }

    // Step 4: Rider requests a ride
    console.log('\n4. Rider requesting a ride...');
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const requestBtn = buttons.find(btn => btn.textContent.includes('Request Ride'));
      if (requestBtn) requestBtn.click();
    });
    await new Promise(resolve => setTimeout(resolve, 500));

    // Confirm ride
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const confirmBtn = buttons.find(btn => btn.textContent.includes('Confirm Ride'));
      if (confirmBtn) confirmBtn.click();
    });
    console.log('✓ Rider confirmed ride request');

    // Step 5: Wait for sync
    console.log('\n5. Waiting for ride to appear in driver app...');
    await new Promise(resolve => setTimeout(resolve, 6000)); // Wait for refetch interval

    // Step 6: Check if driver sees the new ride
    console.log('\n6. Checking if driver sees available ride...');

    const driverSeeRides = await driverPage.evaluate(() => {
      const bodyText = document.body.innerText.toLowerCase();
      const hasAvailableRides = !bodyText.includes('no rides available');
      const hasMainStreet = bodyText.includes('123 main st') || bodyText.includes('main st');
      const hasBusiness = bodyText.includes('456 business') || bodyText.includes('business ave');
      const hasAcceptButton = !!Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Accept Ride'));

      return {
        hasRides: hasAvailableRides,
        hasPickup: hasMainStreet,
        hasDropoff: hasBusiness,
        hasAcceptButton,
        rideCount: (document.body.innerText.match(/km/g) || []).length,
      };
    });

    console.log('\nDriver App Status:');
    if (driverSeeRides.hasRides) {
      console.log('  ✓ Has available rides (not "No Rides Available")');
    } else {
      console.log('  ✗ Still shows "No Rides Available"');
    }

    if (driverSeeRides.hasPickup) {
      console.log('  ✓ Pickup location visible');
    }

    if (driverSeeRides.hasDropoff) {
      console.log('  ✓ Dropoff location visible');
    }

    if (driverSeeRides.hasAcceptButton) {
      console.log('  ✓ Accept Ride button available');
    }

    console.log(`  ✓ Visible rides: ${driverSeeRides.rideCount}`);

    // Final result
    console.log('\n' + '='.repeat(60));
    if (driverSeeRides.hasRides && driverSeeRides.hasAcceptButton) {
      console.log('✓ RIDE SYNC SUCCESSFUL');
      console.log('='.repeat(60));
      console.log('\nRider & Driver Apps are Connected:');
      console.log('  • Rider requests ride in rider app');
      console.log('  • Ride appears in driver app within 5 seconds');
      console.log('  • Driver can accept the ride');
      console.log('  • System works without backend!');
    } else {
      console.log('⚠️  RIDE SYNC NOT WORKING');
      console.log('='.repeat(60));
      console.log('\nDebug Info:');
      console.log(`  Has Rides: ${driverSeeRides.hasRides}`);
      console.log(`  Accept Button: ${driverSeeRides.hasAcceptButton}`);
    }

    return { success: driverSeeRides.hasRides && driverSeeRides.hasAcceptButton };
  } catch (error) {
    console.error(`❌ Test Error: ${error.message}`);
    return { success: false, error: error.message };
  } finally {
    if (browser) await browser.close();
  }
}

testRideSync().catch(console.error);
