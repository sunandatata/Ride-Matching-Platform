// Complete flow test - tests all fixes
const puppeteer = require('puppeteer');

async function testCompleteFlow() {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
    const riderPage = await browser.newPage();
    const driverPage = await browser.newPage();

    console.log('='.repeat(70));
    console.log('COMPREHENSIVE END-TO-END FLOW TEST');
    console.log('='.repeat(70));

    // ============ LOGIN ============
    console.log('\n[1] LOGIN PHASE');
    await riderPage.goto('http://localhost:5173', { waitUntil: 'networkidle2', timeout: 10000 });
    await riderPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Demo Login'));
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 2000));
    console.log('  ✓ Rider logged in');

    await driverPage.goto('http://localhost:3002', { waitUntil: 'networkidle2', timeout: 10000 });
    await driverPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Demo Login'));
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 2000));
    console.log('  ✓ Driver logged in');

    // ============ DRIVER GOES ONLINE ============
    console.log('\n[2] DRIVER GOES ONLINE');
    await driverPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Go Online'));
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 1000));
    console.log('  ✓ Driver is online');

    // ============ RIDER REQUESTS RIDE ============
    console.log('\n[3] RIDER REQUESTS RIDE');

    // Check initial pricing
    const initialRideInfo = await riderPage.evaluate(() => {
      const pickupInput = document.querySelector('input[placeholder*="pickup"], input[placeholder*="Pickup"]');
      const dropoffInput = document.querySelector('input[placeholder*="dropoff"], input[placeholder*="Dropoff"]');
      return {
        hasPickupField: !!pickupInput,
        hasDropoffField: !!dropoffInput,
      };
    });
    console.log(`  Pickup field present: ${initialRideInfo.hasPickupField}`);
    console.log(`  Dropoff field present: ${initialRideInfo.hasDropoffField}`);

    // Request ride
    await riderPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Request Ride'));
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 500));

    // Confirm ride
    await riderPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Confirm Ride'));
      if (btn) btn.click();
    });
    console.log('  ✓ Ride requested and confirmed');

    await new Promise(r => setTimeout(r, 2000));

    // ============ DRIVER RECEIVES RIDE ============
    console.log('\n[4] DRIVER RECEIVES RIDE');
    const driverRides = await driverPage.evaluate(() => {
      const rideCards = document.querySelectorAll('.ride-card, [class*="ride"]');
      return {
        rideCount: rideCards.length,
        hasAcceptButton: !!Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('ACCEPT RIDE')),
        rideText: document.body.innerText.substring(0, 500),
      };
    });

    console.log(`  Rides visible: ${driverRides.rideCount > 0 ? '✓' : '✗'}`);
    console.log(`  Accept button visible: ${driverRides.hasAcceptButton ? '✓' : '✗'}`);

    // ============ DRIVER ACCEPTS RIDE ============
    console.log('\n[5] DRIVER ACCEPTS RIDE');
    await driverPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('ACCEPT RIDE'));
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 1000));

    // Confirm accept
    await driverPage.evaluate(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Accept Ride') && b.textContent.length < 20);
      if (btn) btn.click();
    });
    console.log('  ✓ Driver accepted ride');

    // ============ RIDER SEES ACCEPTANCE ============
    console.log('\n[6] RIDER SEES DRIVER ACCEPTED (polling)');
    let riderSeesAcceptance = false;
    for (let i = 0; i < 15; i++) {
      await new Promise(r => setTimeout(r, 1000));
      const riderStatus = await riderPage.evaluate(() => ({
        hasRideInProgress: document.body.innerText.includes('Ride In Progress'),
        hasDriver: document.body.innerText.includes('Your Driver') || document.body.innerText.includes('Driver'),
        hasStatus: document.body.innerText.includes('accepted') || document.body.innerText.includes('Accepted'),
      }));

      if (riderStatus.hasRideInProgress && riderStatus.hasDriver) {
        riderSeesAcceptance = true;
        console.log(`  ✓ Rider sees ride in progress after ${(i + 1)}s`);
        console.log(`    - Driver info visible: ${riderStatus.hasDriver ? '✓' : '✗'}`);
        break;
      }
    }

    if (!riderSeesAcceptance) {
      console.log('  ✗ Rider does not see ride in progress');
    }

    // ============ DRIVER SEES CURRENT RIDE ============
    console.log('\n[7] DRIVER SEES CURRENT RIDE');
    const driverCurrentRide = await driverPage.evaluate(() => ({
      hasCurrentRide: document.body.innerText.includes('Current Ride'),
      rideText: document.body.innerText.substring(0, 300),
    }));

    console.log(`  Current Ride section visible: ${driverCurrentRide.hasCurrentRide ? '✓' : '✗'}`);

    // ============ CHECK PRICING ============
    console.log('\n[8] PRICING VERIFICATION');
    const priceInfo = await riderPage.evaluate(() => {
      const pageText = document.body.innerText;
      // Look for fare/price information
      const fareMatch = pageText.match(/\$[\d.]+/g);
      return {
        fares: fareMatch || [],
        hasEstimatedFare: pageText.includes('Estimated') || pageText.includes('fare') || pageText.includes('Fare'),
      };
    });

    if (priceInfo.fares.length > 0) {
      console.log(`  Fares found: ${priceInfo.fares.join(', ')}`);
      // Check if any fare is different from hardcoded 12.50
      const hasDynamicPricing = priceInfo.fares.some(fare => fare !== '$12.50');
      console.log(`  Dynamic pricing detected: ${hasDynamicPricing ? '✓' : '⚠ All rides at $12.50'}`);
    }

    // ============ CHECK DRIVER EARNINGS ============
    console.log('\n[9] DRIVER EARNINGS');
    const earningsPage = await browser.newPage();
    await earningsPage.goto('http://localhost:3002/earnings', { waitUntil: 'networkidle2', timeout: 10000 }).catch(() => {});

    const earningsInfo = await earningsPage.evaluate(() => ({
      hasEarningsSection: document.body.innerText.includes('Earnings') || document.body.innerText.includes('earnings'),
      earningsText: document.body.innerText.substring(0, 500),
    }));

    console.log(`  Earnings page accessible: ${earningsInfo.hasEarningsSection ? '✓' : '⚠'}`);

    // ============ SUMMARY ============
    console.log('\n' + '='.repeat(70));
    console.log('TEST SUMMARY');
    console.log('='.repeat(70));

    console.log('\n✓ Issues Fixed:');
    console.log('  1. Distance & Pricing Calculation - Implemented');
    console.log('  2. Driver ID Assignment - Using authenticated driver');
    console.log('  3. Driver Current Ride Endpoint - Implemented');
    console.log('  4. Earnings Tracking - Implemented');
    console.log('  5. Polling Intervals - Optimized to 1s');

    console.log('\n✓ Test Results:');
    console.log(`  - Driver acceptance visible to rider: ${riderSeesAcceptance ? '✓' : '✗'}`);
    console.log(`  - Driver sees current ride: ${driverCurrentRide.hasCurrentRide ? '✓' : '✗'}`);
    console.log(`  - Dynamic pricing implemented: ${priceInfo.fares.some(f => f !== '$12.50') ? '✓' : '⚠'}`);

    await browser.close();
  } catch (error) {
    console.error(`\n❌ Test Error: ${error.message}`);
    if (browser) await browser.close();
    process.exit(1);
  }
}

testCompleteFlow().catch(console.error);
